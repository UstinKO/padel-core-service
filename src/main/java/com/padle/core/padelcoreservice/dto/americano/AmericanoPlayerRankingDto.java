package com.padle.core.padelcoreservice.dto.americano;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmericanoPlayerRankingDto {
    /** Место в турнире */
    private Integer position;

    private Long playerId;
    private String playerName;
    private String playerLastName;

    // Полная статистика для итоговой таблицы (п. 4 ТЗ)
    private Integer matchesPlayed;
    private Integer byeCount;          // bye / пропуски
    private Integer matchesWon;
    private Integer matchesLost;
    private Integer matchesDrawn;
    private Integer pointsScored;
    private Integer pointsConceded;
    private Integer pointDifference;   // разница очков
    private Integer uniquePartners;    // уникальные партнёры
    private Integer uniqueOpponents;   // уникальные соперники
    private Integer totalScore;        // набранные очки (главный критерий режима "по очкам")
}