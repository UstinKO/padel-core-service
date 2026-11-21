package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Круговые места; мини-таблица строится только внутри группы с равными общими показателями. */
public final class RoundRobinStandings {
    private RoundRobinStandings() {}

    public record Ranked(AmericanoTeam team, int position) {}
    private record Meetings(int wins, int difference) {
        Meetings plus(Meetings other) {
            return new Meetings(wins + other.wins, difference + other.difference);
        }
    }

    public static List<Ranked> rank(List<AmericanoTeam> teams, List<AmericanoMatch> matches,
                                    RoundRobinScoringMode mode) {
        if (mode == RoundRobinScoringMode.POINTS) {
            var ordered = teams.stream().sorted(RoundRobinRules.order(mode)).toList();
            return IntStream.range(0, ordered.size()).mapToObj(i -> new Ranked(ordered.get(i), i + 1)).toList();
        }
        var meetings = teams.stream().collect(Collectors.toMap(AmericanoTeam::getId,
                team -> meetings(team, tiedIds(team, teams), matches)));
        Comparator<AmericanoTeam> sporting = Comparator.comparing(AmericanoTeam::getMatchesWon).reversed()
                .thenComparing(Comparator.comparing(AmericanoTeam::getGameDifference).reversed())
                .thenComparing(Comparator.comparing((AmericanoTeam t) -> meetings.get(t.getId()).wins()).reversed())
                .thenComparing(Comparator.comparing((AmericanoTeam t) -> meetings.get(t.getId()).difference()).reversed());
        return teams.stream().sorted(sporting.thenComparing(AmericanoTeam::getTeamNumber)
                        .thenComparing(AmericanoTeam::getId))
                .map(team -> new Ranked(team, 1 + (int) teams.stream().filter(t -> sporting.compare(t, team) < 0).count()))
                .toList();
    }

    private static Set<Long> tiedIds(AmericanoTeam team, List<AmericanoTeam> teams) {
        return teams.stream().filter(t -> Objects.equals(t.getMatchesWon(), team.getMatchesWon())
                        && Objects.equals(t.getGameDifference(), team.getGameDifference()))
                .map(AmericanoTeam::getId).collect(Collectors.toSet());
    }

    private static Meetings meetings(AmericanoTeam team, Set<Long> tied, List<AmericanoMatch> matches) {
        return matches.stream().filter(AmericanoMatch::isCompleted)
                .filter(m -> m.getRound().getPhase() == null && m.getTeam1Score() != null && m.getTeam2Score() != null)
                .filter(m -> tied.contains(m.getTeam1Id()) && tied.contains(m.getTeam2Id()))
                .filter(m -> Objects.equals(m.getTeam1Id(), team.getId()) || Objects.equals(m.getTeam2Id(), team.getId()))
                .map(m -> result(m, team.getId())).reduce(new Meetings(0, 0), Meetings::plus);
    }

    private static Meetings result(AmericanoMatch match, Long teamId) {
        boolean first = Objects.equals(match.getTeam1Id(), teamId);
        int scored = first ? match.getTeam1Score() : match.getTeam2Score();
        int conceded = first ? match.getTeam2Score() : match.getTeam1Score();
        return new Meetings(scored > conceded ? 1 : 0, scored - conceded);
    }
}
