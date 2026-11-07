package com.padle.core.padelcoreservice.service.calendar;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import org.springframework.web.util.UriUtils;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarEventWriterTest {
    @Test
    void preservesUtf8EscapesAndFoldsByOctets() {
        String title = "Король корта ñ 🎾".repeat(12);
        CalendarEvent event = event(title, "Линия 1\nLínea 2, semi; back\\\r\nBEGIN:VALARM");
        String ics = new String(CalendarEventWriter.iCalendar(event), StandardCharsets.UTF_8);
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n", "\r", "�");
        assertThat(ics.split("\r\n")).allSatisfy(line ->
                assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75));
        String unfolded = ics.replace("\r\n ", "");
        assertThat(unfolded).contains("SUMMARY:" + title + "\r\n", "UID:tournament-42@1-padel.com\r\n",
                "DTSTART:20261102T213000Z\r\n", "DTEND:20261102T230000Z\r\n",
                "DESCRIPTION:Линия 1\\nLínea 2\\, semi\\; back\\\\\\nBEGIN:VALARM\r\n");
        assertThat(unfolded).doesNotContain("\r\nBEGIN:VALARM", "ATTENDEE", "ORGANIZER");
        assertThat(ics).endsWith("END:VCALENDAR\r\n");
    }

    @Test
    void googleContainsSameEventAndUsesTemplateWithoutPersonalAccess() {
        CalendarEvent event = event("Американо C8 · Парное участие — 1-Padel", "Línea 1\nАктуальная страница https://1-padel.com/torneo/42");
        String url = CalendarEventWriter.googleUrl(event);
        var params = UriComponentsBuilder.fromUriString(url).build().getQueryParams();
        assertThat(url).startsWith("https://calendar.google.com/calendar/render?");
        assertThat(decoded(params.getFirst("action"))).isEqualTo("TEMPLATE");
        assertThat(decoded(params.getFirst("text"))).isEqualTo(event.title());
        assertThat(decoded(params.getFirst("details"))).isEqualTo(event.description());
        assertThat(decoded(params.getFirst("location"))).isEqualTo(event.location());
        assertThat(decoded(params.getFirst("dates"))).isEqualTo("20261102T213000Z/20261102T230000Z");
    }

    private String decoded(String value) {
        return UriUtils.decode(value, StandardCharsets.UTF_8);
    }

    private CalendarEvent event(String title, String description) {
        return new CalendarEvent("tournament-42@1-padel.com", title,
                Instant.parse("2026-11-02T21:30:00Z"), Instant.parse("2026-11-02T23:00:00Z"),
                EventSchedule.DEFAULT_ZONE, "Club Ñandú, Av. Córdoba 123; CABA", description,
                "https://1-padel.com/torneo/42", Instant.parse("2026-11-01T12:00:00Z"));
    }
}
