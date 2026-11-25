package com.padle.core.padelcoreservice.mapper.americano;

import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.service.americano.RoundRobinPointsStandingsEngine;
import com.padle.core.padelcoreservice.service.americano.RoundRobinStandings;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RoundRobinPointsStandingsMapper {

    @BeanMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
    @Mapping(target = "index", source = "index")
    @Mapping(target = "id", source = "team.id")
    @Mapping(target = "number", source = "team.teamNumber")
    @Mapping(target = "totalScore", source = "team.totalScore")
    @Mapping(target = "wins", source = "team.matchesWon")
    @Mapping(target = "pointsScored", source = "team.pointsScored")
    @Mapping(target = "pointsConceded", source = "team.pointsConceded")
    @Mapping(target = "losses", source = "team.matchesLost")
    RoundRobinPointsStandingsEngine.Team toTeam(AmericanoTeam team, int index);

    @Mapping(target = "team", source = "index", qualifiedByName = "originalTeam")
    RoundRobinStandings.Ranked toRanked(RoundRobinPointsStandingsEngine.Ranked ranked,
                                       @Context List<AmericanoTeam> teams);

    @Named("originalTeam")
    default AmericanoTeam originalTeam(int index, @Context List<AmericanoTeam> teams) {
        return teams.get(index);
    }
}
