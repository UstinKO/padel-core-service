package com.padle.core.padelcoreservice.service.americano;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Места кругового SETS; входные встречи уже отобраны как завершённые матчи кругового этапа. */
public final class RoundRobinSetStandingsEngine {

    private RoundRobinSetStandingsEngine() {
    }

    public record Team(int index, Long id, Integer number, Integer wins, Integer gameDifference) {
    }

    public record Match(Long firstId, Long secondId, int firstScore, int secondScore) {
    }

    public record Ranked(int index, int position) {
    }

    private record Meetings(int wins, int difference) {
        Meetings plus(Meetings other) {
            return new Meetings(wins + other.wins, difference + other.difference);
        }
    }

    public static List<Ranked> rank(List<Team> teams, List<Match> matches) {
        var meetings = teams.stream().collect(Collectors.toMap(Team::id,
                team -> meetings(team, tiedIds(team, teams), matches)));
        Comparator<Team> sporting = Comparator.comparing(Team::wins).reversed()
                .thenComparing(Comparator.comparing(Team::gameDifference).reversed())
                .thenComparing(Comparator.comparing((Team team) -> meetings.get(team.id()).wins()).reversed())
                .thenComparing(Comparator.comparing((Team team) -> meetings.get(team.id()).difference()).reversed());
        return teams.stream().sorted(sporting.thenComparing(Team::number).thenComparing(Team::id))
                .map(team -> new Ranked(team.index(), 1 + (int) teams.stream()
                        .filter(other -> sporting.compare(other, team) < 0).count()))
                .toList();
    }

    private static Set<Long> tiedIds(Team team, List<Team> teams) {
        return teams.stream().filter(other -> Objects.equals(other.wins(), team.wins())
                        && Objects.equals(other.gameDifference(), team.gameDifference()))
                .map(Team::id).collect(Collectors.toSet());
    }

    private static Meetings meetings(Team team, Set<Long> tied, List<Match> matches) {
        return matches.stream().filter(match -> tied.contains(match.firstId()) && tied.contains(match.secondId()))
                .filter(match -> Objects.equals(match.firstId(), team.id()) || Objects.equals(match.secondId(), team.id()))
                .map(match -> result(match, team.id())).reduce(new Meetings(0, 0), Meetings::plus);
    }

    private static Meetings result(Match match, Long teamId) {
        boolean first = Objects.equals(match.firstId(), teamId);
        int scored = first ? match.firstScore() : match.secondScore();
        int conceded = first ? match.secondScore() : match.firstScore();
        return new Meetings(scored > conceded ? 1 : 0, scored - conceded);
    }
}
