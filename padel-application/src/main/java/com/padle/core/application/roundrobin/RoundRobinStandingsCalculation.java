package com.padle.core.application.roundrobin;

import com.padle.core.padelcoreservice.service.americano.RoundRobinPointsStandingsEngine;
import com.padle.core.padelcoreservice.service.americano.RoundRobinSetStandingsEngine;
import java.util.List;
import java.util.stream.IntStream;

import static com.padle.core.application.roundrobin.RoundRobinPositionInput.*;
import static com.padle.core.application.roundrobin.RoundRobinReadSet.ScoringMode;
import static com.padle.core.application.roundrobin.RoundRobinReadSet.Status;

final class RoundRobinStandingsCalculation {
    private RoundRobinStandingsCalculation() {}

    static List<RoundRobinPosition> rank(RoundRobinPositionInput values) {
        var teams = values.teams();
        if (values.mode() == ScoringMode.POINTS) {
            var inputs = IntStream.range(0, teams.size()).mapToObj(index -> {
                var team = teams.get(index);
                return new RoundRobinPointsStandingsEngine.Team(index, team.id(), team.number(), team.totalScore(),
                        team.matchesWon(), team.pointsScored(), team.pointsConceded(), team.matchesLost());
            }).toList();
            return RoundRobinPointsStandingsEngine.rank(inputs).stream()
                    .map(row -> new RoundRobinPosition(row.index(), teams.get(row.index()).id(), row.position())).toList();
        }
        if (teams.isEmpty()) {
            return List.of();
        }
        var inputs = IntStream.range(0, teams.size()).mapToObj(index -> {
            var team = teams.get(index);
            return new RoundRobinSetStandingsEngine.Team(index, team.id(), team.number(),
                    team.matchesWon(), team.gameDifference());
        }).toList();
        var meetings = values.matches().stream().filter(match -> match.status() == Status.COMPLETED)
                .filter(match -> match.phase() == null && match.firstScore() != null && match.secondScore() != null)
                .map(match -> new RoundRobinSetStandingsEngine.Match(match.firstId(), match.secondId(),
                        match.firstScore(), match.secondScore())).toList();
        return RoundRobinSetStandingsEngine.rank(inputs, meetings).stream()
                .map(row -> new RoundRobinPosition(row.index(), teams.get(row.index()).id(), row.position())).toList();
    }

    static List<RoundRobinPosition> rankRead(List<RoundRobinReadSet.Team> teams,
                                           List<RoundRobinReadSet.Match> matches, ScoringMode mode) {
        var inputs = teams.stream().map(team -> {
            var stats = team.statistics();
            return new PositionTeam(team.id(), team.number(), stats.totalScore(), stats.wins(), stats.pointsScored(),
                    stats.pointsConceded(), stats.losses(), mode == ScoringMode.SETS ? stats.gameDifference() : null);
        }).toList();
        var meetings = mode == ScoringMode.SETS && !teams.isEmpty() ? matches.stream()
                .map(match -> new PositionMatch(match.firstId(), match.secondId(), match.phase(), match.status(),
                        match.firstScore(), match.secondScore())).toList() : List.<PositionMatch>of();
        return rank(new RoundRobinPositionInput(mode, inputs, meetings));
    }
}
