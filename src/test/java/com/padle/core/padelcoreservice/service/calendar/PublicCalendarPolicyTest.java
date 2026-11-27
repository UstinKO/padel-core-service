package com.padle.core.padelcoreservice.service.calendar;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.calendar.PublicCalendarFeed.Availability;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import static org.assertj.core.api.Assertions.*;

class PublicCalendarPolicyTest {
    @ParameterizedTest
    @CsvSource({"2021-02,28", "2026-05,35", "2026-08,42", "2026-12,35"})
    void monthRangeContainsWholeWeeks(String month, int days) {
        var range = PublicCalendarRange.of(month);
        assertThat(ChronoUnit.DAYS.between(range.from(), range.to()) + 1).isEqualTo(days);
        assertThat(range.from().getDayOfWeek().getValue()).isEqualTo(1);
        assertThat(range.to().getDayOfWeek().getValue()).isEqualTo(7);
    }

    @Test
    void boundariesAndInvalidMonths() {
        var range = PublicCalendarRange.of("2026-11");
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(range.to()).isEqualTo(LocalDate.of(2026, 12, 6));
        assertThat(PublicCalendarRange.of("2026-12").to()).isEqualTo(LocalDate.of(2027, 1, 3));
        assertThatThrownBy(() -> PublicCalendarRange.of("2026-13")).hasMessage("INVALID_CATALOG_FILTER");
        assertThatThrownBy(() -> PublicCalendarRange.of("2026-1")).hasMessage("INVALID_CATALOG_FILTER");
    }

    @ParameterizedTest
    @CsvSource({"C8_C7,C7,true", "C4_C3,C3,true", "C5,C3,false", "TODOS,C7,false",
            "TODOS,TODOS,true", "SUMA_14,SUMA_14,true", "SUMA_15,SUMA_14,false", "D7_D8,D7,true"})
    void declaredLevelsMatchOnlyTheirOwnValues(String declared, Nivel selected, boolean expected) {
        assertThat(DeclaredEventLevel.matches(declared, selected)).isEqualTo(expected);
        assertThat(DeclaredEventLevel.including(selected).contains(Nivel.fromValue(declared))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0,AVAILABLE", "6,AVAILABLE", "7,LIMITED", "9,LIMITED", "10,WAITLIST", "11,WAITLIST"})
    void existingCapacityThresholdsArePreserved(int occupied, Availability expected) {
        var event = event();
        event.setInscritosActuales(occupied);
        assertThat(PublicRegistrationAvailability.of(event, Instant.parse("2026-01-01T00:00:00Z")))
                .isEqualTo(expected);
    }

    @Test
    void closedStatusAndLocalStartAreIndependentFromCapacity() {
        var event = event();
        assertThat(PublicRegistrationAvailability.of(event, Instant.parse("2026-12-05T20:59:59Z")))
                .isEqualTo(Availability.AVAILABLE);
        assertThat(PublicRegistrationAvailability.of(event, Instant.parse("2026-12-05T21:00:00Z")))
                .isEqualTo(Availability.CLOSED);
        event.setEstado(TournamentStatus.CERRADO);
        assertThat(PublicRegistrationAvailability.of(event, Instant.parse("2026-01-01T00:00:00Z")))
                .isEqualTo(Availability.CLOSED);
    }

    private static TournamentDto event() {
        return TournamentDto.builder().fechaInicio(LocalDate.of(2026, 12, 5)).horaInicio(LocalTime.of(18, 0))
                .timeZone(EventSchedule.DEFAULT_ZONE).estado(TournamentStatus.REGISTRO_ABIERTO)
                .cupoMax(10).inscritosActuales(0).build();
    }
}
