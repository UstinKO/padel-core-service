package com.padle.core.padelcoreservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KingOfCourtStateDTO {
    private Long tournamentId;
    private Long kingId;
    private Integer currentRound;
    private Integer calibrationRounds;
    private Integer maxCourts;
    private Boolean isActive;
    private Boolean isFinished;
    private Boolean allResultsIn;
    private String youtubeLink;
    private List<CourtDTO> courts;
    private List<PlayerStatsDTO> ranking;
    private List<MatchHistoryDTO> history;
    private Long currentRoundId;
}