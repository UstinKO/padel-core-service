package com.padle.core.padelcoreservice.mapper.americano;

import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoPlayerDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoPlayerRankingDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoPlayer;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AmericanoMapper {

    // ── AmericanoPlayer → AmericanoPlayerDto ────────────────────────────────

    @Mapping(source = "player.id",       target = "playerId")
    @Mapping(source = "player.nombre",   target = "playerName")
    @Mapping(source = "player.apellido", target = "playerLastName")
    @Mapping(expression = "java(player.getPlayer().getNombre() + \" \" + player.getPlayer().getApellido())",
            target = "playerFullName")
    @Mapping(source = "player.email",    target = "playerEmail")
    @Mapping(source = "player.telefono", target = "playerPhone")
    @Mapping(target = "pointDifference", expression = "java(player.getPointDifference())")
    @Mapping(target = "isActive",
            expression = "java(player.getStatus() == com.padle.core.padelcoreservice.model.enums.AmericanoPlayerStatus.ACTIVE)")
        // новые поля маппятся напрямую (имена совпадают)
    AmericanoPlayerDto toDto(AmericanoPlayer player);

    List<AmericanoPlayerDto> toPlayerDtoList(List<AmericanoPlayer> players);

    // ── AmericanoRound → AmericanoRoundDto ───────────────────────────────────

    @Mapping(source = "tournament.id", target = "tournamentId")
    @Mapping(source = "courts",        target = "courts")
    @Mapping(target = "totalMatches",
            expression = "java(round.getMatches() != null ? round.getMatches().size() : 0)")
    @Mapping(target = "completedMatches",
            expression = "java(round.getMatches() != null ? (int) round.getMatches().stream()" +
                    ".filter(m -> m.getStatus() == com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus.COMPLETED).count() : 0)")
    // byePlayerNames вычисляется в сервисе — маппер не имеет доступа к списку всех игроков турнира
    @Mapping(target = "byePlayerNames", ignore = true)
    AmericanoRoundDto toDto(AmericanoRound round);

    List<AmericanoRoundDto> toRoundDtoList(List<AmericanoRound> rounds);

    // ── AmericanoMatch → AmericanoMatchDto ───────────────────────────────────

    @Mapping(source = "round.id",           target = "roundId")
    @Mapping(source = "round.roundNumber",  target = "roundNumber")
    @Mapping(source = "tournament.id",      target = "tournamentId")
    @Mapping(source = "courtNumber",        target = "courtNumber")

    @Mapping(source = "team1Player1.id",       target = "team1Player1Id")
    @Mapping(source = "team1Player1.nombre",   target = "team1Player1Name")
    @Mapping(source = "team1Player1.apellido", target = "team1Player1LastName")
    @Mapping(source = "team1Player2.id",       target = "team1Player2Id")
    @Mapping(source = "team1Player2.nombre",   target = "team1Player2Name")
    @Mapping(source = "team1Player2.apellido", target = "team1Player2LastName")

    @Mapping(source = "team2Player1.id",       target = "team2Player1Id")
    @Mapping(source = "team2Player1.nombre",   target = "team2Player1Name")
    @Mapping(source = "team2Player1.apellido", target = "team2Player1LastName")
    @Mapping(source = "team2Player2.id",       target = "team2Player2Id")
    @Mapping(source = "team2Player2.nombre",   target = "team2Player2Name")
    @Mapping(source = "team2Player2.apellido", target = "team2Player2LastName")

    @Mapping(expression = "java(match.getTeam1DisplayName())", target = "team1DisplayName")
    @Mapping(expression = "java(match.getTeam2DisplayName())", target = "team2DisplayName")
    @Mapping(target = "isCompleted",   expression = "java(match.isCompleted())")
    @Mapping(target = "isTeam1Winner", expression = "java(match.isTeam1Winner())")
    AmericanoMatchDto toDto(AmericanoMatch match);

    List<AmericanoMatchDto> toMatchDtoList(List<AmericanoMatch> matches);

    // ── AmericanoPlayer → AmericanoPlayerRankingDto ──────────────────────────

    @Mapping(source = "player.id",       target = "playerId")
    @Mapping(source = "player.nombre",   target = "playerName")
    @Mapping(source = "player.apellido", target = "playerLastName")
    @Mapping(target = "pointDifference", expression = "java(player.getPointDifference())")
    // position проставляется вручную в сервисе после сортировки
    @Mapping(target = "position", ignore = true)
    AmericanoPlayerRankingDto toRankingDto(AmericanoPlayer player);
}