package com.padle.core.padelcoreservice.service.contact;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Tournament;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class OrganizerContacts {
    private static final PhoneNumberUtil PHONES = PhoneNumberUtil.getInstance();
    private static final Pattern PHONE = Pattern.compile("\\+?[0-9\\s().-]+");
    private static final Pattern NAMED_LEGACY = Pattern.compile("^([^0-9+]{1,160}?)\\s*(\\+[0-9][0-9\\s().-]*)$");
    private static final String REQUIRED = "organizer.contact.phone.required";
    private static final String INVALID = "organizer.contact.phone.invalid";
    private static final String COUNTRY = "organizer.contact.country.invalid";
    private static final String NAME = "organizer.contact.name_in_phone";
    private static final String LINK = "organizer.contact.link.invalid";

    private OrganizerContacts() {}

    public record Contact(String name, String phone, String country, String displayPhone,
                          String whatsappUrl, boolean needsReview, String errorCode) {
        public boolean valid() { return errorCode.isEmpty(); }
    }

    public record Country(String code, String label, String callingCode, String example) {}

    public static Contact input(TournamentDto dto) {
        return dto.getOrganizerPhone() == null
                ? legacy(dto.getContactoOrganizador())
                : validate(dto.getOrganizerName(), dto.getOrganizerPhone(), dto.getOrganizerCountry());
    }

    public static Contact resolve(Tournament tournament) {
        return tournament.getOrganizerPhone() == null
                ? legacy(tournament.getContactoOrganizador())
                : validate(tournament.getOrganizerName(), tournament.getOrganizerPhone(), tournament.getOrganizerCountry());
    }

    public static Contact validate(String name, String input, String country) {
        String value = clean(input);
        String region = clean(country).isEmpty() ? "AR" : country.strip().toUpperCase(Locale.ROOT);
        if (!PHONES.getSupportedRegions().contains(region)) return error(COUNTRY);
        if (value.isEmpty()) return error(REQUIRED);
        if (clean(name).length() > 160 || value.length() > 500) return error(INVALID);
        if (value.contains("://") || value.startsWith("wa.me/")) {
            return fromLink(clean(name), value, region);
        }
        if (!PHONE.matcher(value).matches()) return error(NAME);
        return number(clean(name), value, region);
    }

    public static Contact legacy(String raw) {
        String value = clean(raw);
        Contact direct = validate("", value, "AR");
        if (direct.valid()) return direct;
        var match = NAMED_LEGACY.matcher(value);
        if (match.matches()) {
            String name = match.group(1).strip().replaceAll("[\\s:;—-]+$", "");
            Contact named = validate(name, match.group(2), "AR");
            if (named.valid()) return named;
        }
        return direct;
    }

    private static Contact number(String name, String value, String region) {
        try {
            // Международный AR без плюса однозначен только при полном коде и длине.
            String digits = value.replaceAll("[^0-9]", "");
            String candidate = !value.startsWith("+") && "AR".equals(region)
                    && digits.startsWith("54") && (digits.length() == 12 || digits.length() == 13)
                    ? "+" + digits : value;
            var parsed = PHONES.parse(candidate, region);
            if (parsed.hasExtension() || !PHONES.isValidNumber(parsed)) return error(INVALID);
            String actualRegion = PHONES.getRegionCodeForNumber(parsed);
            if (!PHONES.getSupportedRegions().contains(actualRegion)) return error(COUNTRY);
            String e164 = PHONES.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
            return new Contact(name, e164, actualRegion,
                    PHONES.format(parsed, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL),
                    "https://wa.me/" + e164.substring(1), false, "");
        } catch (NumberParseException exception) {
            return error(exception.getErrorType() == NumberParseException.ErrorType.TOO_SHORT_NSN
                    || exception.getErrorType() == NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD ? REQUIRED : INVALID);
        }
    }

    private static Contact fromLink(String name, String value, String region) {
        try {
            URI uri = URI.create(value.startsWith("wa.me/") ? "https://" + value : value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || uri.getPort() != -1 || uri.getFragment() != null) return error(LINK);
            String host = clean(uri.getHost()).toLowerCase(Locale.ROOT);
            String phone;
            if ("wa.me".equals(host)) {
                if (Arrays.stream(clean(uri.getRawQuery()).split("&"))
                        .map(part -> part.split("=", 2)[0])
                        .anyMatch(key -> "phone".equals(URLDecoder.decode(key, StandardCharsets.UTF_8)))) return error(LINK);
                phone = uri.getRawPath().replaceFirst("^/", "");
            } else if ("api.whatsapp.com".equals(host) && ("/send".equals(uri.getPath()) || "/send/".equals(uri.getPath()))) {
                List<String> numbers = Arrays.stream(clean(uri.getRawQuery()).split("&"))
                        .map(part -> part.split("=", 2))
                        .filter(part -> part.length == 2 && "phone".equals(URLDecoder.decode(part[0], StandardCharsets.UTF_8)))
                        .map(part -> URLDecoder.decode(part[1], StandardCharsets.UTF_8).strip()).toList();
                if (numbers.size() != 1) return error(LINK);
                phone = numbers.get(0);
            } else return error(LINK);
            if (!phone.matches("\\+?[0-9]{7,15}")) return error(LINK);
            return number(name, phone.startsWith("+") ? phone : "+" + phone, region);
        } catch (IllegalArgumentException exception) {
            return error(LINK);
        }
    }

    public static List<Country> countries(Locale locale) {
        var names = PHONES.getSupportedRegions().stream().collect(Collectors.toMap(code -> code,
                code -> new Locale.Builder().setRegion(code).build().getDisplayCountry(locale)));
        var collator = Collator.getInstance(locale);
        Map<String, Integer> priority = Map.of("AR", 0, "RU", 1);
        return names.keySet().stream().sorted(Comparator.comparingInt((String code) -> priority.getOrDefault(code, 2))
                .thenComparing(names::get, collator)).map(code -> country(code, names.get(code))).toList();
    }

    private static Country country(String code, String name) {
        String callingCode = "+" + PHONES.getCountryCodeForRegion(code);
        var sample = PHONES.getExampleNumberForType(code, PhoneNumberUtil.PhoneNumberType.MOBILE);
        String example = sample == null ? callingCode
                : PHONES.format(sample, PhoneNumberUtil.PhoneNumberFormat.NATIONAL);
        return new Country(code, name + " (" + callingCode + ")", callingCode, example);
    }

    private static Contact error(String code) {
        return new Contact("", "", "AR", "", "", true, code);
    }

    private static String clean(String value) { return Objects.requireNonNullElse(value, "").strip(); }
}
