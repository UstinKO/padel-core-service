package com.padle.core.padelcoreservice.dto.americano;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class TeamAmericanoRankingDto {
    private Long tournamentId;
    private String tournamentName;
    private Integer totalRounds;
    private Integer completedRounds;
    private Integer totalTeams;
    private boolean isFinished;       // все матчи сыграны
    private List<AmericanoTeamDto> ranking;
    private com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode scoringMode;
    private Integer pointsPerMatch;
    private Integer courts;
    private boolean roundRobinComplete;
    private List<RoundRobinAdditionalMatchDto> additionalMatches;
    private List<RoundRobinStandingDto> finalRanking;
}
