package com.padle.core.padelcoreservice.mapper.americano;

import com.padle.core.application.roundrobin.RoundRobinRanking;
import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoRankingDto;
import com.padle.core.padelcoreservice.dto.americano.RoundRobinAdditionalMatchDto;
import com.padle.core.padelcoreservice.dto.americano.RoundRobinStandingDto;
import org.mapstruct.Mapper;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RoundRobinRankingMapper {
    @Mapping(target = "isFinished", source = "finished")
    TeamAmericanoRankingDto toDto(RoundRobinRanking ranking);

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "id", source = "team.id")
    @Mapping(target = "tournamentId", source = "team.tournamentId")
    @Mapping(target = "teamNumber", source = "team.number")
    @Mapping(target = "currentPosition", source = "position")
    @Mapping(target = "status", source = "team.status")
    @Mapping(target = "displayName", source = "team.displayName")
    @Mapping(target = "player1Id", source = "team.player1.id")
    @Mapping(target = "player1Name", source = "team.player1.name")
    @Mapping(target = "player1Email", source = "team.player1.email")
    @Mapping(target = "player1Phone", source = "team.player1.phone")
    @Mapping(target = "player1Nivel", source = "team.player1.level")
    @Mapping(target = "player1PlayingSide", source = "team.player1.side")
    @Mapping(target = "player2Id", source = "team.player2.id")
    @Mapping(target = "player2Email", source = "team.player2.email")
    @Mapping(target = "player2Nivel", source = "team.player2.level")
    @Mapping(target = "player2PlayingSide", source = "team.player2.side")
    @Mapping(target = "totalScore", source = "team.statistics.totalScore")
    @Mapping(target = "matchesPlayed", source = "team.statistics.played")
    @Mapping(target = "matchesWon", source = "team.statistics.wins")
    @Mapping(target = "matchesLost", source = "team.statistics.losses")
    @Mapping(target = "matchesDrawn", source = "team.statistics.drawn")
    @Mapping(target = "pointsScored", source = "team.statistics.pointsScored")
    @Mapping(target = "pointsConceded", source = "team.statistics.pointsConceded")
    @Mapping(target = "setsWon", source = "team.statistics.setsWon")
    @Mapping(target = "setsLost", source = "team.statistics.setsLost")
    @Mapping(target = "gamesWon", source = "team.statistics.gamesWon")
    @Mapping(target = "gamesLost", source = "team.statistics.gamesLost")
    @Mapping(target = "player2Name", expression = "java(row.team().player2() == null ? row.team().guestName() : row.team().player2().name())")
    @Mapping(target = "player2Phone", expression = "java(row.team().player2() == null ? row.team().guestPhone() : row.team().player2().phone())")
    @Mapping(target = "pointDifference", expression = "java(row.team().statistics().pointDifference())")
    @Mapping(target = "gameDifference", expression = "java(row.team().statistics().gameDifference())")
    @Mapping(target = "setDifference", ignore = true)
    @Mapping(target = "match1GamesWon", ignore = true)
    @Mapping(target = "match1GamesLost", ignore = true)
    @Mapping(target = "match2GamesWon", ignore = true)
    @Mapping(target = "match2GamesLost", ignore = true)
    @Mapping(target = "qualStatus", ignore = true)
    @Mapping(target = "tournamentStatus", ignore = true)
    @Mapping(target = "queuePosition", ignore = true)
    @Mapping(target = "queueOpponentName", ignore = true)
    @Mapping(target = "eliminatedStage", ignore = true)
    @Mapping(target = "registrationSource", ignore = true)
    @Mapping(target = "hasPaid", ignore = true)
    @Mapping(target = "attended", ignore = true)
    @Mapping(target = "adminComment", ignore = true)
    @Mapping(target = "waitingSeconds", ignore = true)
    @Mapping(target = "maximumWaitingSeconds", ignore = true)
    @Mapping(target = "totalWaitingSeconds", ignore = true)
    @Mapping(target = "waitingSince", ignore = true)
    AmericanoTeamDto toTeamDto(RoundRobinRanking.RankedTeam row);

    RoundRobinAdditionalMatchDto toAdditionalDto(RoundRobinRanking.AdditionalMatch match);

    RoundRobinStandingDto toStandingDto(RoundRobinRanking.FinalStanding standing);

    default List<RoundRobinAdditionalMatchDto> toAdditionalDtos(List<RoundRobinRanking.AdditionalMatch> matches) {
        return matches.stream().map(this::toAdditionalDto).toList();
    }

    default List<RoundRobinStandingDto> toStandingDtos(List<RoundRobinRanking.FinalStanding> standings) {
        return standings.stream().map(this::toStandingDto).toList();
    }
}
