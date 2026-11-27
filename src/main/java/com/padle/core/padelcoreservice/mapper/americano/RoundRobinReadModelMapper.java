package com.padle.core.padelcoreservice.mapper.americano;

import com.padle.core.application.roundrobin.RoundRobinReadSet;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.application.tournament.Nivel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.ValueMapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RoundRobinReadModelMapper {
    @Mapping(target = "name", source = "nombre")
    RoundRobinReadSet.Event toEvent(Tournament tournament);

    @Mapping(target = "tournamentId", source = "tournament.id")
    @Mapping(target = "number", source = "teamNumber")
    @Mapping(target = "guestName", source = "player2Name")
    @Mapping(target = "guestPhone", source = "player2Phone")
    @Mapping(target = "statistics", source = "team")
    RoundRobinReadSet.Team toTeam(AmericanoTeam team);

    @Mapping(target = "name", expression = "java(player.getNombre() + \" \" + player.getApellido())")
    @Mapping(target = "phone", source = "telefono")
    @Mapping(target = "level", source = "nivelJugador", qualifiedByName = "level")
    @Mapping(target = "side", source = "playingSide")
    RoundRobinReadSet.Player toPlayer(PlayerPadel player);

    @Named("level")
    @ValueMapping(source = "SIN_ESPECIFICAR", target = MappingConstants.NULL)
    String level(Nivel level);

    @Mapping(target = "played", source = "matchesPlayed")
    @Mapping(target = "wins", source = "matchesWon")
    @Mapping(target = "losses", source = "matchesLost")
    @Mapping(target = "drawn", source = "matchesDrawn")
    RoundRobinReadSet.Statistics toStatistics(AmericanoTeam team);

    RoundRobinReadSet.Round toRound(AmericanoRound round);

    @Mapping(target = "firstId", source = "team1Id")
    @Mapping(target = "secondId", source = "team2Id")
    @Mapping(target = "phase", source = "round.phase")
    @Mapping(target = "firstScore", source = "team1Score")
    @Mapping(target = "secondScore", source = "team2Score")
    @Mapping(target = "court", source = "courtNumber")
    @Mapping(target = "label", source = "note")
    RoundRobinReadSet.Match toMatch(AmericanoMatch match);
}
