package com.padle.core.application.roundrobin;

import com.padle.core.padelcoreservice.service.americano.RoundRobinFinalOrderEngine;
import com.padle.core.padelcoreservice.service.americano.RoundRobinPointsStandingsEngine;
import com.padle.core.padelcoreservice.service.americano.RoundRobinSetStandingsEngine;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.padle.core.application.roundrobin.RoundRobinReadSet.*;
import static com.padle.core.application.roundrobin.RoundRobinRanking.*;

public final class RoundRobinRankingComposer {
    private RoundRobinRankingComposer() {}

    public static RoundRobinRanking compose(RoundRobinReadSet values) {
        var circle = values.rounds().stream().filter(round -> round.phase() == null).toList();
        var first = circle.stream().findFirst();
        var mode = first.map(Round::scoringMode).orElse(ScoringMode.POINTS);
        var ranking = standings(values.rankingTeams(), values.allMatches(), mode);
        int completed = (int) circle.stream().filter(round -> round.status() == Status.COMPLETED).count();
        var inputs = IntStream.range(0, ranking.size()).mapToObj(index -> new RoundRobinFinalOrderEngine.Standing(
                index, ranking.get(index).team().id(), ranking.get(index).position())).toList();
        var extra = values.allMatches().stream().filter(match -> match.phase() == Phase.PLACEMENT)
                .map(match -> new RoundRobinFinalOrderEngine.Match(match.firstId(), match.secondId(),
                        match.firstScore(), match.secondScore(), match.status() == Status.COMPLETED, match.placementStart()))
                .toList();
        var finalRanking = RoundRobinFinalOrderEngine.rank(inputs, extra).stream().map(row -> {
            var team = ranking.get(row.index()).team();
            return new FinalStanding(team.id(), team.number(), team.displayName(), row.position());
        }).toList();
        return new RoundRobinRanking(values.event().id(), values.event().name(), circle.size(), completed,
                values.rankingTeams().size(), !circle.isEmpty() && values.rounds().stream()
                        .allMatch(round -> round.status() == Status.COMPLETED), mode,
                first.map(Round::pointsPerMatch).orElse(32), first.map(Round::courts).orElse(1),
                !circle.isEmpty() && completed == circle.size(), ranking, additional(values), finalRanking);
    }

    private static List<RankedTeam> standings(List<Team> teams, List<Match> matches, ScoringMode mode) {
        if (mode == ScoringMode.POINTS) {
            var inputs = IntStream.range(0, teams.size()).mapToObj(index -> {
                var team = teams.get(index);
                var stats = team.statistics();
                return new RoundRobinPointsStandingsEngine.Team(index, team.id(), team.number(), stats.totalScore(),
                        stats.wins(), stats.pointsScored(), stats.pointsConceded(), stats.losses());
            }).toList();
            return RoundRobinPointsStandingsEngine.rank(inputs).stream()
                    .map(row -> new RankedTeam(teams.get(row.index()), row.position())).toList();
        }
        if (teams.isEmpty()) return List.of();
        var inputs = IntStream.range(0, teams.size()).mapToObj(index -> {
            var team = teams.get(index);
            return new RoundRobinSetStandingsEngine.Team(index, team.id(), team.number(),
                    team.statistics().wins(), team.statistics().gameDifference());
        }).toList();
        var completed = matches.stream().filter(match -> match.status() == Status.COMPLETED)
                .filter(match -> match.phase() == null && match.firstScore() != null && match.secondScore() != null)
                .map(match -> new RoundRobinSetStandingsEngine.Match(match.firstId(), match.secondId(),
                        match.firstScore(), match.secondScore())).toList();
        return RoundRobinSetStandingsEngine.rank(inputs, completed).stream()
                .map(row -> new RankedTeam(teams.get(row.index()), row.position())).toList();
    }

    private static List<AdditionalMatch> additional(RoundRobinReadSet values) {
        var teams = values.additionalTeams().stream().collect(Collectors.toMap(Team::id, Function.identity()));
        return values.additionalMatches().stream().map(match -> {
            var first = teams.get(match.firstId());
            var second = teams.get(match.secondId());
            return new AdditionalMatch(match.id(), match.placementStart(), match.label(), match.court(),
                    first.id(), first.number(), first.displayName(), second.id(), second.number(), second.displayName(),
                    match.firstScore(), match.secondScore(), match.status());
        }).toList();
    }
}
