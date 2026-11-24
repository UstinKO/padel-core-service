package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentDto;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public final class TournamentListOrder {
    private static final Comparator<TournamentDto> UPCOMING = Comparator
            .comparing(TournamentDto::getFechaInicio, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TournamentDto::getHoraInicio, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TournamentDto::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    private static final Comparator<TournamentDto> PAST = Comparator
            .comparing(TournamentDto::getFechaInicio, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TournamentDto::getHoraInicio, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(TournamentDto::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private TournamentListOrder() {}

    public static List<TournamentDto> sort(List<TournamentDto> events, LocalDate today) {
        Comparator<TournamentDto> order = Comparator
                .comparingInt((TournamentDto event) -> group(event, today))
                .thenComparing((first, second) -> (group(first, today) == 0 ? UPCOMING : PAST)
                        .compare(first, second));
        return events.stream().sorted(order).toList();
    }

    private static int group(TournamentDto event, LocalDate today) {
        if (event.getFechaInicio() == null) {
            return 2;
        }
        return event.getFechaInicio().isBefore(today) ? 1 : 0;
    }
}
