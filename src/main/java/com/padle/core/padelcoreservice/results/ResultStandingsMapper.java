package com.padle.core.padelcoreservice.results;

import com.padle.core.padelcoreservice.dto.PlayerStatsDTO;
import com.padle.core.padelcoreservice.dto.americano.AmericanoPlayerRankingDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ResultStandingsMapper {
    @Mapping(target = "position", source = "rank")
    @Mapping(target = "played", source = "gamesPlayed")
    @Mapping(target = "draws", ignore = true)
    @Mapping(target = "courtBonus", source = "bonusPoints")
    @Mapping(target = "adjustment", source = "additionalPoints.total")
    @Mapping(target = "totalPoints", source = "totalPoints")
    @Mapping(target = "unit", constant = "POINTS")
    ResultReport.Standing king(PlayerStatsDTO source);

    @Mapping(target = "played", source = "matchesPlayed")
    @Mapping(target = "wins", source = "matchesWon")
    @Mapping(target = "losses", source = "matchesLost")
    @Mapping(target = "draws", source = "matchesDrawn")
    @Mapping(target = "courtBonus", ignore = true)
    @Mapping(target = "adjustment", source = "additionalPoints.total")
    @Mapping(target = "totalPoints", source = "totalScore")
    @Mapping(target = "unit", constant = "POINTS")
    ResultReport.Standing americano(AmericanoPlayerRankingDto source);
}
