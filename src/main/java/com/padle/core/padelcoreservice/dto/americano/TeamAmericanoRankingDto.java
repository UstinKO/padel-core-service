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
}