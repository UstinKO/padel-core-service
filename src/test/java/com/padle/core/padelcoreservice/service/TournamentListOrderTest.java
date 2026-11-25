package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TournamentListOrderTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);

    @Test
    void nearestDatesAndEarlierTimesComeFirstRegardlessOfInputOrId() {
        var late = event(1, TODAY.plusDays(2), "18:00");
        var next = event(2, TODAY.plusDays(3), "15:00");
        var early = event(8, TODAY.plusDays(2), "14:00");
        var today = event(9, TODAY, "09:00");
        var input = List.of(next, late, today, early);

        assertThat(TournamentListOrder.sort(input, TODAY)).containsExactly(today, early, late, next);
        assertThat(input).containsExactly(next, late, today, early);
    }

    @Test
    void keepsTodayAndAllStatusesWithRecentPastFirstAfterFuture() {
        var cancelled = event(1, TODAY.plusDays(1), "14:00");
        cancelled.setEstado(TournamentStatus.CANCELADO);
        var finishedToday = event(2, TODAY, "08:00");
        finishedToday.setEstado(TournamentStatus.FINALIZADO);
        var old = event(3, TODAY.minusDays(2), "20:00");
        var yesterdayEarly = event(4, TODAY.minusDays(1), "14:00");
        var yesterdayLate = event(5, TODAY.minusDays(1), "18:00");

        assertThat(TournamentListOrder.sort(List.of(old, cancelled, yesterdayEarly, yesterdayLate, finishedToday), TODAY))
                .containsExactly(finishedToday, cancelled, yesterdayLate, yesterdayEarly, old);
    }

    @Test
    void identicalStartsUseStableIdAndMissingScheduleComesLast() {
        var second = event(2, TODAY, "14:00");
        var first = event(1, TODAY, "14:00");
        var noTime = TournamentDto.builder().id(3L).fechaInicio(TODAY).build();
        var noDate = TournamentDto.builder().id(4L).build();

        assertThat(TournamentListOrder.sort(List.of(noDate, second, noTime, first), TODAY))
                .containsExactly(first, second, noTime, noDate);
        assertThat(TournamentListOrder.sort(List.of(), TODAY)).isEmpty();
    }

    @Test
    void usesDisplayedLocalScheduleWithoutBrowserTimeZoneConversion() {
        var earlier = event(2, TODAY.plusDays(1), "10:00");
        earlier.setTimeZone("America/Argentina/Buenos_Aires");
        var later = event(1, TODAY.plusDays(1), "11:00");
        later.setTimeZone("Europe/Madrid");

        assertThat(TournamentListOrder.sort(List.of(later, earlier), TODAY)).containsExactly(earlier, later);
    }

    private static TournamentDto event(long id, LocalDate date, String time) {
        return TournamentDto.builder().id(id).fechaInicio(date).horaInicio(LocalTime.parse(time)).build();
    }
}
