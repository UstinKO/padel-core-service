package com.padle.core.padelcoreservice.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.padle.core.padelcoreservice.exception.ContactValidationException;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.identity.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PlayerContactService {
    private static final PhoneNumberUtil PHONES = PhoneNumberUtil.getInstance();
    private static final Pattern PHONE_CHARACTERS = Pattern.compile("[+0-9\\s().-]+");
    private static final Pattern TELEGRAM = Pattern.compile("@[A-Za-z][A-Za-z0-9_]{4,31}");
    private final PlayerRepository players;
    private final PlayerService playerService;
    private final PlayerPhoneGuard phoneGuard;

    public record Contact(String telefono, String telegramUsername) {}

    public static Contact normalize(String phone, String country, String telegram) {
        String value = Objects.requireNonNullElse(phone, "").strip();
        if (value.isEmpty()) throw invalid("PHONE_REQUIRED", "telefono");
        if (value.length() > 80 || !PHONE_CHARACTERS.matcher(value).matches()) {
            throw invalid("PHONE_INVALID", "telefono");
        }
        String region = Objects.requireNonNullElse(country, "AR").toUpperCase(Locale.ROOT);
        if (!PHONES.getSupportedRegions().contains(region)) throw invalid("PHONE_COUNTRY_INVALID", "telefono");
        String normalizedPhone = normalizePhone(value, region);
        String handle = Objects.requireNonNullElse(telegram, "").strip();
        if (!handle.isEmpty() && !handle.startsWith("@")) handle = "@" + handle;
        if (!handle.isEmpty() && !TELEGRAM.matcher(handle).matches()) {
            throw invalid("TELEGRAM_INVALID", "telegramUsername");
        }
        return new Contact(normalizedPhone, handle);
    }

    private static String normalizePhone(String value, String region) {
        try {
            var number = PHONES.parse(value, region);
            var possibility = PHONES.isPossibleNumberWithReason(number);
            if (possibility != PhoneNumberUtil.ValidationResult.IS_POSSIBLE) {
                String code = switch (possibility) {
                    case TOO_SHORT, IS_POSSIBLE_LOCAL_ONLY -> "PHONE_TOO_SHORT";
                    case TOO_LONG -> "PHONE_TOO_LONG";
                    default -> "PHONE_INVALID";
                };
                throw invalid(code, "telefono");
            }
            if (number.hasExtension()) throw invalid("PHONE_INVALID", "telefono");
            return PHONES.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            String code = switch (e.getErrorType()) {
                case TOO_SHORT_AFTER_IDD, TOO_SHORT_NSN -> "PHONE_TOO_SHORT";
                case TOO_LONG -> "PHONE_TOO_LONG";
                default -> "PHONE_INVALID";
            };
            throw invalid(code, "telefono");
        }
    }

    @Transactional
    public Contact update(Long playerId, String phone, String country, String telegram) {
        Contact contact = normalize(phone, country, telegram);
        PlayerPadel player = players.findById(playerId).orElseThrow();
        if (!contact.telegramUsername().isEmpty()
                && !contact.telegramUsername().equals(player.getTelegramUsername())
                && playerService.existsByTelegramUsername(contact.telegramUsername())) {
            throw invalid("TELEGRAM_IN_USE", "telegramUsername");
        }
        phoneGuard.requireAvailable(contact.telefono(), playerId);
        player.setTelefono(contact.telefono());
        player.setTelegramUsername(contact.telegramUsername().isEmpty() ? null : contact.telegramUsername());
        playerService.save(player);
        return contact;
    }

    private static ContactValidationException invalid(String code, String field) {
        return new ContactValidationException(code, field);
    }
}
