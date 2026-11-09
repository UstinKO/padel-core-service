package com.padle.core.padelcoreservice.service.calendar;

import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public final class CalendarEventWriter {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private CalendarEventWriter() {}

    public static String googleUrl(CalendarEvent event) {
        return UriComponentsBuilder.fromUriString("https://calendar.google.com/calendar/render")
                .queryParam("action", "TEMPLATE")
                .queryParam("text", "{title}")
                .queryParam("dates", UTC.format(event.startsAt()) + "/" + UTC.format(event.endsAt()))
                .queryParam("ctz", "{zone}")
                .queryParam("details", "{description}")
                .queryParam("location", "{location}")
                .encode().buildAndExpand(event.title(), event.timeZone(), event.description(), event.location())
                .toUriString();
    }

    public static byte[] iCalendar(CalendarEvent event) {
        return List.of("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//1-Padel//Event Calendar//EN",
                        "CALSCALE:GREGORIAN", "METHOD:PUBLISH", "BEGIN:VEVENT", "UID:" + event.uid(),
                        "DTSTAMP:" + UTC.format(event.updatedAt()),
                        "DTSTART:" + UTC.format(event.startsAt()), "DTEND:" + UTC.format(event.endsAt()),
                        "SUMMARY:" + escape(event.title()), "LOCATION:" + escape(event.location()),
                        "DESCRIPTION:" + escape(event.description()), "URL:" + event.url(),
                        "END:VEVENT", "END:VCALENDAR").stream()
                .map(CalendarEventWriter::fold).collect(Collectors.joining("\r\n", "", "\r\n"))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\r\n", "\n").replace("\r", "\n")
                .replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,");
    }

    private static String fold(String line) {
        StringBuilder folded = new StringBuilder();
        int octets = 0;
        for (int offset = 0; offset < line.length();) {
            int codePoint = line.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int length = character.getBytes(StandardCharsets.UTF_8).length;
            if (octets + length > 75) {
                folded.append("\r\n ");
                octets = 1;
            }
            folded.append(character);
            octets += length;
            offset += Character.charCount(codePoint);
        }
        return folded.toString();
    }
}
