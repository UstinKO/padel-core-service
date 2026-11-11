package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import org.mapstruct.*;
import java.util.List;

@Mapper(componentModel = "spring")
public interface TeamPlayoffRoundMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target="id", source="round.id")
    @Mapping(target="tournamentId", source="round.tournament.id")
    @Mapping(target="roundNumber", source="round.roundNumber")
    @Mapping(target="status", source="round.status")
    @Mapping(target="courts", source="round.courts")
    @Mapping(target="pointsPerMatch", source="round.pointsPerMatch")
    @Mapping(target="note", source="round.note")
    @Mapping(target="startedAt", source="round.startedAt")
    @Mapping(target="completedAt", source="round.completedAt")
    @Mapping(target="matches", source="matches")
    @Mapping(target="totalMatches", expression="java(matches.size())")
    @Mapping(target="completedMatches", expression="java((int) matches.stream().filter(m -> Boolean.TRUE.equals(m.getIsCompleted())).count())")
    AmericanoRoundDto round(AmericanoRound round, List<AmericanoMatchDto> matches);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target="id", source="source.id")
    @Mapping(target="tournamentId", source="source.tournamentId")
    @Mapping(target="roundNumber", source="wave")
    @Mapping(target="status", source="source.status")
    @Mapping(target="courts", source="source.courts")
    @Mapping(target="pointsPerMatch", source="source.pointsPerMatch")
    @Mapping(target="note", source="source.note")
    @Mapping(target="startedAt", source="source.startedAt")
    @Mapping(target="completedAt", source="source.completedAt")
    @Mapping(target="matches", source="matches")
    @Mapping(target="totalMatches", expression="java(matches.size())")
    @Mapping(target="completedMatches", expression="java((int) matches.stream().filter(m -> Boolean.TRUE.equals(m.getIsCompleted())).count())")
    AmericanoRoundDto wave(AmericanoRoundDto source, int wave, List<AmericanoMatchDto> matches);
}
