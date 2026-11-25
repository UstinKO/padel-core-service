package com.padle.core.padelcoreservice.mapper.americano;

import com.padle.core.application.roundrobin.RoundRobinPosition;
import com.padle.core.application.roundrobin.RoundRobinPositionInput.PositionMatch;
import com.padle.core.application.roundrobin.RoundRobinPositionInput.PositionTeam;
import com.padle.core.application.roundrobin.RoundRobinReadSet.ScoringMode;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RoundRobinPositionMapper {
    ScoringMode toMode(RoundRobinScoringMode mode);

    @Mapping(target = "number", source = "team.teamNumber")
    @Mapping(target = "gameDifference", source = "team", qualifiedByName = "modeDifference")
    PositionTeam toTeam(AmericanoTeam team, @Context ScoringMode mode);

    @Mapping(target = "firstId", source = "team1Id")
    @Mapping(target = "secondId", source = "team2Id")
    @Mapping(target = "phase", source = "round.phase")
    @Mapping(target = "firstScore", source = "team1Score")
    @Mapping(target = "secondScore", source = "team2Score")
    PositionMatch toMatch(AmericanoMatch match);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "currentPosition", source = "position.position")
    void apply(RoundRobinPosition position, @MappingTarget AmericanoTeam team);

    /** POINTS не вычисляет разницу геймов; nullable-поле записи остаётся незаполненным. */
    @Named("modeDifference")
    default Integer difference(AmericanoTeam team, @Context ScoringMode mode) {
        return mode == ScoringMode.SETS ? team.getGameDifference() : null;
    }
}
