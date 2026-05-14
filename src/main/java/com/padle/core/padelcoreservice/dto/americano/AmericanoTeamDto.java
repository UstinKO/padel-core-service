package com.padle.core.padelcoreservice.dto.americano;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmericanoTeamDto {

    private Long id;
    private Long tournamentId;
    private Integer teamNumber;
    private Integer currentPosition;
    private String status;

    // Игроки
    private Long player1Id;
    private String player1Name;
    private String player1Email;
    private String player1Phone;

    private Long player2Id;
    private String player2Name;   // из БД или из поля player2Name
    private String player2Email;
    private String player2Phone;  // из БД или из поля player2Phone

    private String displayName;   // "Иван Иванов / Пётр Петров"

    // Статистика
    private Integer totalScore;
    private Integer matchesPlayed;
    private Integer matchesWon;
    private Integer matchesLost;
    private Integer matchesDrawn;
    private Integer pointsScored;
    private Integer pointsConceded;
    private Integer pointDifference;

    // Статистика сетов/геймов (AMERICANO_TEAMS)
    private Integer setsWon;
    private Integer setsLost;
    private Integer gamesWon;
    private Integer gamesLost;
    private Integer setDifference;
    private Integer gameDifference;

    // Данные регистрации (AMERICANO_TEAMS)
    private String registrationSource;
    private Boolean hasPaid;
    private Boolean attended;
    private String adminComment;
}