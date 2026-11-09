package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.TournamentType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record TournamentSocialDraft(
        Long id,
        Format format,
        String category,
        LocalDate date,
        String club,
        List<String> winners,
        List<String> finalists,
        Integer winnerGames,
        Integer finalistGames,
        List<Place> places,
        String resultsPath,
        Upcoming upcoming,
        String name,
        TournamentType type,
        boolean rankingByWins
) {
    public enum Format { TEAM_PLAYOFF, KING_OF_COURT, AMERICANO, GENERAL }

    public record Place(int rank, String name, long points) {}

    public record Upcoming(Long id, TournamentType type, Modalidad modality, LocalDate date,
                           LocalTime time, String club, String registrationPath) {}
}
