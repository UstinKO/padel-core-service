package com.padle.core.padelcoreservice.service.calendar;

import com.padle.core.padelcoreservice.dto.TournamentDto;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EventSchedule {
    public static final String DEFAULT_ZONE = "America/Argentina/Buenos_Aires";
    private static final String NUMBER = "(\\d+(?:[.,]\\d+)?)";
    private static final String UNIT = "(h|hr|hrs|hour|hours|hora|horas|ч|час|часа|часов"
            + "|min|mins|minute|minutes|minuto|minutos|м|мин|минута|минуты|минут"
            + "|d|day|days|día|días|день|дня|дней)";
    private static final Pattern DURATION = Pattern.compile("^" + NUMBER + "\\s*" + UNIT + "$");
    private static final Pattern RANGE = Pattern.compile(
            "^" + NUMBER + "\\s*" + UNIT + "?\\s*[-–—]\\s*" + NUMBER + "\\s*" + UNIT + "$");
    private static final Pattern CLOCK = Pattern.compile("^(\\d+):([0-5]\\d)$");

    private EventSchedule() {}

    public static ZoneId zone(String value) {
        return ZoneId.of(value == null || value.isBlank() ? DEFAULT_ZONE : value);
    }

    public static Optional<Duration> duration(TournamentDto event) {
        if (event.getDurationMinutes() != null) {
            return Optional.of(event.getDurationMinutes()).filter(minutes -> minutes > 0).map(Duration::ofMinutes);
        }
        return Optional.ofNullable(event.getDuracion()).flatMap(EventSchedule::parseDuration);
    }

    public static Optional<Integer> suggestedDurationMinutes(String description) {
        return parseDuration(description)
                .filter(duration -> duration.getSeconds() % 60 == 0 && duration.toMinutes() <= Integer.MAX_VALUE)
                .map(duration -> (int) duration.toMinutes());
    }

    static Optional<Duration> parseDuration(String value) {
        String text = value.replace('\u00a0', ' ').replace('\u202f', ' ').strip().toLowerCase(Locale.ROOT);
        Matcher range = RANGE.matcher(text);
        if (range.matches()) {
            String endUnit = range.group(4);
            return durationInUnit(range.group(1), Objects.requireNonNullElse(range.group(2), endUnit))
                    .flatMap(start -> durationInUnit(range.group(3), endUnit)
                            .map(end -> start.compareTo(end) > 0 ? start : end));
        }
        Matcher clock = CLOCK.matcher(text);
        if (clock.matches()) {
            return positiveSeconds(new BigDecimal(clock.group(1)).multiply(BigDecimal.valueOf(3600))
                    .add(new BigDecimal(clock.group(2)).multiply(BigDecimal.valueOf(60))));
        }
        Matcher match = DURATION.matcher(text);
        if (!match.matches()) return Optional.empty();
        return durationInUnit(match.group(1), match.group(2));
    }

    private static Optional<Duration> durationInUnit(String value, String unit) {
        int seconds = unit.startsWith("h") || unit.startsWith("ч") ? 3600
                : unit.startsWith("d") || unit.startsWith("д") ? 86400 : 60;
        return positiveSeconds(new BigDecimal(value.replace(',', '.')).multiply(BigDecimal.valueOf(seconds)));
    }

    private static Optional<Duration> positiveSeconds(BigDecimal seconds) {
        try {
            long count = seconds.longValueExact();
            return count > 0 ? Optional.of(Duration.ofSeconds(count)) : Optional.empty();
        } catch (ArithmeticException exception) {
            return Optional.empty();
        }
    }

    public static ZonedDateTime start(TournamentDto event) {
        LocalDateTime local = event.getFechaInicio().atTime(event.getHoraInicio());
        ZoneId zone = zone(event.getTimeZone());
        if (zone.getRules().getValidOffsets(local).size() != 1) {
            throw new IllegalArgumentException("calendar.unavailable");
        }
        return local.atZone(zone);
    }
}
