package com.padle.core.padelcoreservice.results;

import com.padle.core.application.tournament.AmericanoFinalOrder;
import com.padle.core.padelcoreservice.dto.americano.RankingCriteria;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import com.padle.core.padelcoreservice.service.americano.TeamAmericanoService;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.padle.core.padelcoreservice.results.ResultReport.*;

@Service
@RequiredArgsConstructor
public class ResultSnapshot {
    private static final Set<String> TERMINAL=Set.of("COMPLETED","FINALIZADO","CANCELLED","CANCELADO","WALKOVER");
    private final ResultSource source;
    private final KingOfCourtService kingService;
    private final AmericanoService americano;
    private final TeamAmericanoService circle;
    private final AmericanoRoundRepository rounds;
    private final ResultStandingsMapper mapper;

    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=30)
    @Timed("admin.results.export.snapshot.time")
    public ResultReport read(long id, boolean contacts) {
        var event=source.event(id);
        var king=source.king(id);
        var teams=source.teams(id,contacts);
        var raw=source.games(event,king);
        var people=source.people(id,contacts,raw,teams,king);
        var names=people.stream().collect(Collectors.toMap(Person::id,p->fullName(p)));
        var games=raw.stream().map(game->game(id,game,names)).toList();
        boolean preliminary=!"FINALIZADO".equals(event.status())||games.stream().anyMatch(game->!TERMINAL.contains(game.status()))
                ||("KING_OF_COURT".equals(event.format())&&king.map(run->!source.kingFinished(run)).orElse(false));
        var standings=standings(event,king,teams,games);
        var profiles=people.stream().map(person->participant(person,standings,games,preliminary,contacts));
        var guests=teams.stream().filter(team->team.second()==null&&team.guestName()!=null&&!team.guestName().isBlank())
                .map(team->guest(team,standings,games,preliminary,contacts));
        var participants=Stream.concat(profiles,guests).toList();
        var warnings=warnings(preliminary,people,participants,games,teams,contacts);
        return new ResultReport(event,participants,games,warnings,Instant.now(),contacts,preliminary);
    }

    private Map<Long,Standing> standings(Event event, Optional<Long> king, List<ResultSource.Team> teams, List<Game> games) {
        if ("KING_OF_COURT".equals(event.format())) return king.map(run->kingService.getRanking(run).stream()
                .collect(Collectors.toMap(row->row.getPlayerId(),mapper::king))).orElse(Map.of());
        if ("AMERICANO".equals(event.format())&&"INDIVIDUAL".equals(event.modality())&&americano.isInitialized(event.id())) {
            var order=Optional.ofNullable(event.finalOrder()).map(AmericanoFinalOrder::valueOf).orElse(AmericanoFinalOrder.SCORE_DESC);
            return americano.getRankingWithDetails(event.id(),new RankingCriteria(order.sortBy(),order.ascending())).getRanking().stream()
                    .collect(Collectors.toMap(row->row.getPlayerId(),mapper::americano));
        }
        if (rounds.existsByTournamentIdAndPhaseIsNull(event.id())&&!teams.isEmpty()) return circleStandings(event.id(),teams);
        return playoffStandings(teams,games);
    }

    private Map<Long,Standing> circleStandings(long id, List<ResultSource.Team> teams) {
        var ranking=circle.getRanking(id);
        var places=ranking.getFinalRanking().stream().collect(Collectors.toMap(row->row.teamId(),row->row.position()));
        var byTeam=ranking.getRanking().stream().collect(Collectors.toMap(row->row.getId(),row->new Standing(
                places.get(row.getId()),row.getMatchesPlayed(),row.getMatchesWon(),row.getMatchesLost(),row.getMatchesDrawn(),
                Long.valueOf(row.getPointsScored()),null,null,Long.valueOf(row.getTotalScore()),ranking.getScoringMode().name().equals("SETS")?"GAMES":"POINTS")));
        return playerStandings(teams,byTeam);
    }

    private Map<Long,Standing> playoffStandings(List<ResultSource.Team> teams, List<Game> games) {
        var finals=games.stream().filter(game->"FINAL".equals(game.stage())&&"COMPLETED".equals(game.status()))
                .filter(game->game.score1()!=null&&game.score2()!=null&&!game.score1().equals(game.score2())).toList();
        var byTeam=finals.stream().flatMap(game->Stream.of(
                Map.entry(Objects.requireNonNullElse(game.team1Id(),-1L),place(game.score1()>game.score2()?1:2)),
                Map.entry(Objects.requireNonNullElse(game.team2Id(),-1L),place(game.score2()>game.score1()?1:2))))
                .collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue,(first,second)->second));
        return playerStandings(teams,byTeam);
    }

    private Standing place(int position) { return new Standing(position,null,null,null,null,null,null,null,null,null); }

    private Map<Long,Standing> playerStandings(List<ResultSource.Team> teams, Map<Long,Standing> byTeam) {
        var links=teams.stream().filter(team->byTeam.containsKey(team.id()))
                .flatMap(team->Stream.of(team.first(),team.second()).filter(Objects::nonNull)
                        .map(player->Map.entry(player,byTeam.get(team.id()))))
                .collect(Collectors.groupingBy(Map.Entry::getKey));
        return links.entrySet().stream().filter(entry->entry.getValue().size()==1)
                .collect(Collectors.toMap(Map.Entry::getKey,entry->entry.getValue().get(0).getValue()));
    }

    private Participant participant(Person person, Map<Long,Standing> standings, List<Game> games, boolean preliminary, boolean contacts) {
        var standing=standings.getOrDefault(person.id(),Standing.EMPTY);
        var stage=games.stream().filter(game->hasPlayer(game.team1Players(),person.id())||hasPlayer(game.team2Players(),person.id()))
                .max(Comparator.comparingInt(this::stageOrder).thenComparing(game->Objects.requireNonNullElse(game.round(),0)))
                .map(game->game.stage()+Optional.ofNullable(game.round()).map(round->" / "+round).orElse("")).orElse("");
        var safe=new Person(person.id(),person.firstName(),person.lastName(),contacts?validPhone(person.phone()):null,
                person.status(),person.teamIds(),person.teamNumbers(),person.partnerIds(),person.registrationActive());
        return new Participant(safe,standing,preliminary?null:standing.position(),stage);
    }

    private Participant guest(ResultSource.Team team,Map<Long,Standing> standings,List<Game> games,boolean preliminary,boolean contacts) {
        var standing=Optional.ofNullable(team.first()).map(standings::get).orElse(Standing.EMPTY);
        var stage=games.stream().filter(game->Objects.equals(game.team1Id(),team.id())||Objects.equals(game.team2Id(),team.id()))
                .max(Comparator.comparingInt(this::stageOrder).thenComparing(game->Objects.requireNonNullElse(game.round(),0)))
                .map(Game::stage).orElse("");
        var person=new Person(null,team.guestName(),"",contacts?validPhone(team.guestPhone()):null,"UNLINKED_GUEST",
                Long.toString(team.id()),String.valueOf(team.number()),String.valueOf(team.first()),null);
        return new Participant(person,standing,preliminary?null:standing.position(),stage);
    }

    private int stageOrder(Game game) {
        return switch(game.stage()) { case "QUALIFICATION"->1; case "ROUND_OF_16"->2;case "QUARTER_FINAL"->3;
            case "SEMI_FINAL"->4;case "FINAL"->5;default->0;};
    }

    private boolean hasPlayer(String ids,long id) { return List.of(ids.split(";")).contains(Long.toString(id)); }
    private String validPhone(String phone) {
        return Optional.ofNullable(phone).filter(value->value.matches("\\+?[0-9][0-9 ()-]{5,29}" )).orElse("");
    }
    private String fullName(Person person) { return Stream.of(person.firstName(),person.lastName()).filter(Objects::nonNull).collect(Collectors.joining(" ")); }
    private String ids(List<Long> ids) { return ids.stream().map(String::valueOf).collect(Collectors.joining(";")); }
    private String names(List<Long> ids, String fallback, Map<Long,String> names) {
        var values=ids.stream().map(id->names.getOrDefault(id,"[missing profile #"+id+"]"));
        return Stream.concat(values,Optional.ofNullable(fallback).filter(value->!value.isBlank()).stream()).distinct().collect(Collectors.joining(" / "));
    }

    private Game game(long id, ResultSource.RawGame game, Map<Long,String> names) {
        return new Game(game.source(),game.id(),game.resultId(),id,game.round(),game.stage(),game.court(),game.team1(),
                ids(game.first()),names(game.first(),game.firstFallback(),names),game.team2(),ids(game.second()),
                names(game.second(),game.secondFallback(),names),game.score1(),game.score2(),game.unit(),game.sets(),
                ids(game.winner()),game.status(),game.technical());
    }

    private List<String> warnings(boolean preliminary,List<Person> people,List<Participant> participants,List<Game> games,List<ResultSource.Team> teams,boolean contacts) {
        return Stream.of(preliminary?"PROVISIONAL":"",games.isEmpty()?"NO_MATCHES":"",
                participants.stream().noneMatch(p->p.standing().position()!=null)?"NO_STANDINGS":"",
                !preliminary&&participants.stream().anyMatch(p->p.finalPlace()==null)?"MISSING_PLACEMENTS":"",
                teams.stream().anyMatch(t->t.second()==null&&t.guestName()!=null&&!t.guestName().isBlank())?"LEGACY_GUEST_WITHOUT_PROFILE":"",
                games.stream().anyMatch(g->g.team1Names().isBlank()||g.team2Names().isBlank())?"MISSING_LINEUP":"",
                contacts&&people.stream().anyMatch(p->p.phone()!=null&&!p.phone().isBlank()&&validPhone(p.phone()).isBlank())?"INVALID_PHONE_EXCLUDED":"",
                contacts?"CONTACTS_INCLUDED":"").filter(value->!value.isEmpty()).toList();
    }
}
