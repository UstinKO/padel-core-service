package com.padle.core.padelcoreservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.padle.core.application.identity.PlayingSide;

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
    // Уровень игрока из профиля — для опционального отображения
    // в публичном списке участников (Tournament.mostrarNivel), null если не указан.
    private com.padle.core.application.tournament.Nivel playerNivel;
    private Long totalPoints;
    private Integer bonusPoints;
    private Integer gamesPlayed;
    private Integer wins;
    private Integer losses;
    private Integer rank;
    private Long matchPoints;
    private AdditionalPointsDto additionalPoints;

    @JsonIgnore
    private PlayingSide playerPlayingSide;
}