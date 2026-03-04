package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.*;
import com.padle.core.padelcoreservice.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
@Component
public interface KingOfCourtMapper {

    @Mapping(target = "tournamentId", source = "king.tournament.id")
    @Mapping(target = "kingId", source = "king.id")
    @Mapping(target = "currentRound", source = "king.currentRound")
    @Mapping(target = "calibrationRounds", source = "king.calibrationRounds")
    @Mapping(target = "maxCourts", source = "king.maxCourts")
    @Mapping(target = "isActive", source = "king.isActive")
    @Mapping(target = "isFinished", source = "king.isFinished")
    @Mapping(target = "youtubeLink", source = "king.youtubeLink")
    @Mapping(target = "courts", expression = "java(mapCourts(round != null ? round.getCourts() : java.util.Collections.emptyList()))")
    @Mapping(target = "allResultsIn", ignore = true)
    @Mapping(target = "ranking", ignore = true)
    @Mapping(target = "history", ignore = true)
    @Mapping(target = "currentRoundId", source = "round.id")
    KingOfCourtStateDTO toStateDTO(TournamentKingOfCourt king, KingOfCourtRound round);

    @Mapping(target = "id", source = "court.id")
    @Mapping(target = "courtNumber", source = "court.courtNumber")
    @Mapping(target = "players", source = "court.players")
    @Mapping(target = "teams", source = "court.teams")
    @Mapping(target = "result", source = "court.result")
    @Mapping(target = "hasResult", expression = "java(court.getResult() != null)")
    CourtDTO toCourtDTO(KingOfCourtCourt court);

    @Mapping(target = "id", source = "player.id")
    @Mapping(target = "name", source = "player.nombreCompleto")
    PlayerInfoDTO toPlayerInfoDTO(PlayerPadel player);

    @Mapping(target = "teamNumber", source = "team.teamNumber")
    @Mapping(target = "players", expression = "java(java.util.List.of(toPlayerInfoDTO(team.getPlayer1()), toPlayerInfoDTO(team.getPlayer2())))")
    TeamDTO toTeamDTO(CourtTeam team);

    @Mapping(target = "id", source = "result.id")
    @Mapping(target = "winners", expression = "java(java.util.List.of(result.getWinner1().getId(), result.getWinner2().getId()))")
    @Mapping(target = "losers", expression = "java(java.util.List.of(result.getLoser1().getId(), result.getLoser2().getId()))")
    @Mapping(target = "winnersScore", source = "winnersScore")
    @Mapping(target = "losersScore", source = "losersScore")
    @Mapping(target = "winnerNames", expression = "java(java.util.List.of(result.getWinner1().getNombreCompleto(), result.getWinner2().getNombreCompleto()))")
    @Mapping(target = "loserNames", expression = "java(java.util.List.of(result.getLoser1().getNombreCompleto(), result.getLoser2().getNombreCompleto()))")
    CourtResultDTO toCourtResultDTO(KingOfCourtMatchResult result);

    @Mapping(target = "playerId", source = "stats.player.id")
    @Mapping(target = "playerName", source = "stats.player.nombreCompleto")
    @Mapping(target = "totalPoints", source = "stats.totalPoints")
    @Mapping(target = "bonusPoints", source = "stats.bonusPoints")
    @Mapping(target = "gamesPlayed", source = "stats.gamesPlayed")
    @Mapping(target = "wins", source = "stats.wins")
    @Mapping(target = "losses", source = "stats.losses")
    @Mapping(target = "rank", ignore = true)
    PlayerStatsDTO toPlayerStatsDTO(KingOfCourtPlayerStats stats);

    @Mapping(target = "round", source = "result.court.round.roundNumber")
    @Mapping(target = "courtNumber", source = "result.court.courtNumber")
    @Mapping(target = "winners", expression = "java(java.util.List.of(result.getWinner1().getNombreCompleto(), result.getWinner2().getNombreCompleto()))")
    @Mapping(target = "losers", expression = "java(java.util.List.of(result.getLoser1().getNombreCompleto(), result.getLoser2().getNombreCompleto()))")
    @Mapping(target = "winnersScore", source = "winnersScore")
    @Mapping(target = "losersScore", source = "losersScore")
    @Mapping(target = "score", expression = "java(result.getWinnersScore() + \":\" + result.getLosersScore())")
    @Mapping(target = "formattedResult", expression = "java(formatMatchResult(result))")
    MatchHistoryDTO toMatchHistoryDTO(KingOfCourtMatchResult result);

    default List<CourtDTO> mapCourts(List<KingOfCourtCourt> courts) {
        return courts.stream()
                .map(this::toCourtDTO)
                .collect(Collectors.toList());
    }

    @Named("formatMatchResult")
    default String formatMatchResult(KingOfCourtMatchResult result) {
        return String.format("%s & %s vs %s & %s: %d:%d",
                result.getWinner1().getNombreCompleto(),
                result.getWinner2().getNombreCompleto(),
                result.getLoser1().getNombreCompleto(),
                result.getLoser2().getNombreCompleto(),
                result.getWinnersScore(),
                result.getLosersScore()
        );
    }
}