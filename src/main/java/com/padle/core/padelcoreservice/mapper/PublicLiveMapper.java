package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.*;
import com.padle.core.padelcoreservice.dto.live.PublicLiveDtos.*;
import com.padle.core.padelcoreservice.dto.americano.*;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import org.mapstruct.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Mapper(componentModel = "spring", imports = {Nivel.class, Participant.class})
public interface PublicLiveMapper {
    @Mapping(target="playerNivel", expression="java(showLevels ? source.getPlayerNivel() : null)")
    KingRank kingRank(PlayerStatsDTO source, @Context boolean showLevels);

    @Mapping(target="playerName", expression="java(name(source.getPlayerName(),source.getPlayerLastName()))")
    @Mapping(target="playerNivel", expression="java(showLevels ? source.getPlayerNivel() : null)")
    AmericanoRank americanoRank(AmericanoPlayerRankingDto source, @Context boolean showLevels);

    @Mapping(target="player1", expression="java(new Participant(source.getPlayer1Id(), source.getPlayer1Name(), showLevels ? source.getPlayer1Nivel() : null))")
    @Mapping(target="player2", expression="java(new Participant(source.getPlayer2Id(), source.getPlayer2Name(), showLevels ? source.getPlayer2Nivel() : null))")
    TeamRank teamRank(AmericanoTeamDto source, @Context boolean showLevels);

    @Mapping(target="level", ignore=true)
    Participant participant(PlayerInfoDTO source);

    record MatchLinks(Long nextMatchId, Integer nextMatchSlot, Boolean firstWinner) {}
    @Mapping(target="firstWinner", expression="java(source.outcome().firstWinner())")
    MatchLinks links(AmericanoMatch source);

    default String name(String first, String last) {
        return Stream.of(first,last).filter(Objects::nonNull).filter(value -> !value.isBlank()).collect(Collectors.joining(" "));
    }

    default List<Participant> people(Long firstId, String first, String firstLast, Long secondId, String second, String secondLast) {
        return Stream.of(person(firstId, name(first,firstLast)), person(secondId,name(second,secondLast)))
                .flatMap(Optional::stream).toList();
    }

    default Optional<Participant> person(Long id, String name) {
        return id == null && name.isBlank() ? Optional.empty() : Optional.of(new Participant(id,name,null));
    }

    default Match match(AmericanoMatchDto source, Map<Long, AmericanoMatch> entities, String phase) {
        var first = new Side(source.getTeam1Id(), source.getTeam1Number(), source.getTeam1DisplayName(),
                people(source.getTeam1Player1Id(),source.getTeam1Player1Name(),source.getTeam1Player1LastName(),
                        source.getTeam1Player2Id(),source.getTeam1Player2Name(),source.getTeam1Player2LastName()),
                source.getTeam1Score(), source.getTeam1Games(), source.getTeam1MatchOrdinal());
        var second = new Side(source.getTeam2Id(), source.getTeam2Number(), source.getTeam2DisplayName(),
                people(source.getTeam2Player1Id(),source.getTeam2Player1Name(),source.getTeam2Player1LastName(),
                        source.getTeam2Player2Id(),source.getTeam2Player2Name(),source.getTeam2Player2LastName()),
                source.getTeam2Score(), source.getTeam2Games(), source.getTeam2MatchOrdinal());
        var links = Optional.ofNullable(entities.get(source.getId())).map(this::links).orElseGet(() -> new MatchLinks(null,null,null));
        return new Match(source.getId(),source.getMatchNumber(),source.getCourtNumber(),phase,source.getPlayoffStage(),
                source.getStatus().name(), first, second, Boolean.TRUE.equals(source.getIsCompleted()), links.firstWinner(),
                links.nextMatchId(), links.nextMatchSlot(),
                Boolean.TRUE.equals(source.getPriority()), source.getQueuePosition(),source.getWaitingMinutes());
    }

    default Round round(AmericanoRoundDto source, Map<Long, AmericanoMatch> entities, String phase) {
        return new Round(source.getId(),source.getRoundNumber(),phase,source.getStatus().name(),source.getCourts(),
                source.getMatches().stream().map(value -> match(value,entities,phase)).toList(),
                Objects.requireNonNullElse(source.getByePlayerNames(),List.of()));
    }

    default Match kingCourt(CourtDTO source, String phase, String status) {
        var result = Optional.ofNullable(source.getResult());
        var sides = source.getTeams().stream().map(team -> new Side(null,team.getTeamNumber(),"",
                team.getPlayers().stream().map(this::participant).toList(),null,null,null)).toList();
        var first = sides.isEmpty() ? new Side(null,null,"",source.getPlayers().stream().map(this::participant).toList(),null,null,null) : sides.get(0);
        var second = sides.size() > 1 ? sides.get(1) : new Side(null,null,"",List.of(),null,null,null);
        if(result.isPresent() && sides.size() < 2) {
            var data = result.orElseThrow();
            first = new Side(null,null,"", indexedPeople(data.getWinners(),data.getWinnerNames()),data.getWinnersScore(),null,null);
            second = new Side(null,null,"", indexedPeople(data.getLosers(),data.getLoserNames()),data.getLosersScore(),null,null);
        } else if(result.isPresent()) {
            var data = result.orElseThrow();
            boolean firstWon = first.players().stream().anyMatch(player -> data.getWinners().contains(player.id()));
            first = new Side(null,first.number(),"",first.players(),firstWon ? data.getWinnersScore() : data.getLosersScore(),null,null);
            second = new Side(null,second.number(),"",second.players(),firstWon ? data.getLosersScore() : data.getWinnersScore(),null,null);
        }
        var winningIds = result.map(CourtResultDTO::getWinners).orElse(List.of());
        boolean firstWon = first.players().stream().anyMatch(player -> winningIds.contains(player.id()));
        return new Match(source.getId(), source.getCourtNumber(),source.getCourtNumber(),phase,null,
                result.isPresent() ? "COMPLETED" : status,first,second,result.isPresent(),result.isPresent() ? firstWon : null,
                null,null,false,null,null);
    }

    default List<Participant> indexedPeople(List<Long> ids, List<String> names) {
        return IntStream.range(0,ids.size()).mapToObj(index -> new Participant(ids.get(index),names.get(index),null)).toList();
    }
}
