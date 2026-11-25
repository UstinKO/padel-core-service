package com.padle.core.padelcoreservice.mapper.americano;

import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.service.americano.RoundRobinSetStandingsEngine;
import com.padle.core.padelcoreservice.service.americano.RoundRobinStandings;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RoundRobinSetStandingsMapper {

    @Mapping(target = "index", source = "index")
    @Mapping(target = "id", source = "team.id")
    @Mapping(target = "number", source = "team.teamNumber")
    @Mapping(target = "wins", source = "team.matchesWon")
    @Mapping(target = "gameDifference", source = "team.gameDifference")
    RoundRobinSetStandingsEngine.Team toTeam(AmericanoTeam team, int index);

    @Mapping(target = "firstId", source = "team1Id")
    @Mapping(target = "secondId", source = "team2Id")
    @Mapping(target = "firstScore", source = "team1Score")
    @Mapping(target = "secondScore", source = "team2Score")
    RoundRobinSetStandingsEngine.Match toMatch(AmericanoMatch match);

    @Mapping(target = "team", source = "index", qualifiedByName = "originalTeam")
    RoundRobinStandings.Ranked toRanked(RoundRobinSetStandingsEngine.Ranked ranked,
                                       @Context List<AmericanoTeam> teams);

    @Named("originalTeam")
    default AmericanoTeam originalTeam(int index, @Context List<AmericanoTeam> teams) {
        return teams.get(index);
    }
}
