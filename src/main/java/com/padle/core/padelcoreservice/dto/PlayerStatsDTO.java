package com.padle.core.padelcoreservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStatsDTO {
    private Long playerId;
    private String playerName;
    private Integer totalPoints;
    private Integer bonusPoints;
    private Integer gamesPlayed;
    private Integer wins;
    private Integer losses;
    private Integer rank;
}