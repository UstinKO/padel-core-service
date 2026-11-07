package com.padle.core.padelcoreservice.service.calendar;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventScheduleTest {
    @ParameterizedTest
    @CsvSource({"2 horas,120", "1.5 hours,90", "1 минута,1", "90 minutos,90", "2 часа,120", "1 día,1440", "2:15,135", "'1,5 h',90"})
    void understandsOnlyExplicitDuration(String text, long minutes) {
        assertThat(EventSchedule.parseDuration(text)).contains(Duration.ofMinutes(minutes));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0 h", "-2 h", "unas 2 horas", "2 horas aprox", "2", "2 días o más", "1h30", "999999999999999999999999999999 horas", "0.00001 h"})
    void doesNotInventDuration(String text) {
        assertThat(EventSchedule.parseDuration(text)).isEmpty();
    }

    @Test
    void numericDurationTakesPrecedenceAndCrossesMidnight() {
        TournamentDto event = TournamentDto.builder().durationMinutes(90).duracion("4 horas")
                .fechaInicio(LocalDate.of(2026, 11, 2)).horaInicio(LocalTime.of(23, 30)).build();
        assertThat(EventSchedule.duration(event)).contains(Duration.ofMinutes(90));
        assertThat(EventSchedule.start(event).plus(EventSchedule.duration(event).orElseThrow()).toInstant())
                .isEqualTo(Instant.parse("2026-11-03T04:00:00Z"));
    }

    @Test
    void hostTimeZoneDoesNotAffectStart() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            TournamentDto event = TournamentDto.builder().fechaInicio(LocalDate.of(2026, 11, 2))
                    .horaInicio(LocalTime.of(18, 30)).timeZone("America/Argentina/Buenos_Aires").build();
            assertThat(EventSchedule.start(event).toInstant()).isEqualTo(Instant.parse("2026-11-02T21:30:00Z"));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @ParameterizedTest
    @CsvSource({"2026-03-08,02:30", "2026-11-01,01:30"})
    void rejectsNonexistentAndAmbiguousDstTimes(LocalDate date, LocalTime time) {
        TournamentDto event = TournamentDto.builder().fechaInicio(date).horaInicio(time).timeZone("America/New_York").build();
        assertThatThrownBy(() -> EventSchedule.start(event)).isInstanceOf(IllegalArgumentException.class);
    }
}
