package com.padle.core.padelcoreservice.dto;

import java.util.List;

public record PublicHomeResponse(long totalClubs, long totalPlayers, long totalTournaments,
        List<PublicTournamentDto> upcomingTournaments) {
    public PublicHomeResponse { upcomingTournaments = List.copyOf(upcomingTournaments); }
}
