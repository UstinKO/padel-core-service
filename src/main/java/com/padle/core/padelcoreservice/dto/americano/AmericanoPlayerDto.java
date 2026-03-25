package com.padle.core.padelcoreservice.dto.americano;

import com.padle.core.padelcoreservice.model.enums.AmericanoPlayerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmericanoPlayerDto {
    private Long id;
    private Long tournamentId;
    private Long playerId;
    private String playerName;
    private String playerLastName;
    private String playerFullName;
    private String playerEmail;
    private String playerPhone;
    private Integer initialPosition;
    private Integer currentPosition;
    private AmericanoPlayerStatus status;

    // Основная статистика
    private Integer totalScore;
    private Integer matchesPlayed;
    private Integer matchesWon;
    private Integer matchesLost;
    private Integer matchesDrawn;
    private Integer pointsScored;
    private Integer pointsConceded;
    private Integer pointDifference;

    // Bye / пропуски
    private Integer byeCount;

    // Разнообразие
    private Integer uniquePartners;
    private Integer uniqueOpponents;

    private Boolean isActive;
}