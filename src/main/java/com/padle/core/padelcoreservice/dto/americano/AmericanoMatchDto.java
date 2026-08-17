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

    // AMERICANO_TEAMS: геймы в сете и плей-офф стадия
    private Integer team1Games;
    private Integer team2Games;
    private String playoffStage;
    private Long team1Id;
    private Long team2Id;

    /** LFPT-307: постоянный номер команды (не путать с местом/посевом) — виден на всех стадиях турнира. */
    private Integer team1Number;
    private Integer team2Number;

    /** Issue #298 п.1: "1-й"/"2-й" квалификационный матч для каждой из двух команд (не сквозной matchNumber раунда). */
    private Integer team1MatchOrdinal;
    private Integer team2MatchOrdinal;

    /** Play-in (T13, ТЗ §32): матч желательно запустить как можно раньше. */
    private Boolean priority;
}