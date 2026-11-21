package com.padle.core.padelcoreservice.dto.americano;

import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;

public record RoundRobinAdditionalMatchDto(
        Long id, Integer placementStart, String label, Integer courtNumber,
        Long team1Id, Integer team1Number, String team1Name,
        Long team2Id, Integer team2Number, String team2Name,
        Integer team1Score, Integer team2Score, AmericanoRoundStatus status) {}
