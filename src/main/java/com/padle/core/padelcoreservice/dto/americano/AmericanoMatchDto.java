package com.padle.core.padelcoreservice.dto.americano;

import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmericanoMatchDto {
    private Long id;
    private Long roundId;
    private Long tournamentId;
    private Integer roundNumber;
    private Integer matchNumber;

    // Team 1
    private Long team1Player1Id;
    private String team1Player1Name;
    private String team1Player1LastName;
    private Long team1Player2Id;
    private String team1Player2Name;
    private String team1Player2LastName;
    private String team1DisplayName;

    // Team 2
    private Long team2Player1Id;
    private String team2Player1Name;
    private String team2Player1LastName;
    private Long team2Player2Id;
    private String team2Player2Name;
    private String team2Player2LastName;
    private String team2DisplayName;

    private Integer team1Score;
    private Integer team2Score;
    private Boolean isDoubles;
    private AmericanoRoundStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer courtNumber;
    private String note;
    private Boolean isCompleted;
    private Boolean isTeam1Winner;
}