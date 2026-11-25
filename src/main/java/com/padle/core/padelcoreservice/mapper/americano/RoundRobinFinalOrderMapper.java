package com.padle.core.padelcoreservice.mapper.americano;

import com.padle.core.padelcoreservice.dto.americano.RoundRobinStandingDto;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.service.americano.RoundRobinFinalOrderEngine;
import com.padle.core.padelcoreservice.service.americano.RoundRobinStandings;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RoundRobinFinalOrderMapper {
    @Mapping(target = "index", source = "index")
    @Mapping(target = "teamId", expression = "java(ranked.team().getId())")
    @Mapping(target = "position", source = "ranked.position")
    RoundRobinFinalOrderEngine.Standing toStanding(RoundRobinStandings.Ranked ranked, int index);

    @Mapping(target = "firstId", source = "team1Id")
    @Mapping(target = "secondId", source = "team2Id")
    @Mapping(target = "firstScore", source = "team1Score")
    @Mapping(target = "secondScore", source = "team2Score")
    @Mapping(target = "completed", source = "completed")
    @Mapping(target = "placementStart", source = "placementStart")
    RoundRobinFinalOrderEngine.Match toMatch(AmericanoMatch match);

    @Mapping(target = "teamId", source = "team.id")
    @Mapping(target = "teamNumber", source = "team.teamNumber")
    @Mapping(target = "displayName", source = "team.displayName")
    @Mapping(target = "position", source = "position")
    RoundRobinStandingDto toDto(AmericanoTeam team, int position);
}
