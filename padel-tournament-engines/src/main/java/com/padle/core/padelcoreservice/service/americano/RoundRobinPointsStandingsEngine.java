package com.padle.core.padelcoreservice.service.americano;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/** Места POINTS последовательны; полностью равные строки сохраняют исходный порядок. */
public final class RoundRobinPointsStandingsEngine {

    private RoundRobinPointsStandingsEngine() {
    }

    public record Team(int index, Long id, Integer number, Integer totalScore, Integer wins,
                       Integer pointsScored, Integer pointsConceded, Integer losses) {
    }

    public record Ranked(int index, int position) {
    }

    public static Comparator<Team> order() {
        return Comparator.comparing(Team::totalScore).reversed()
                .thenComparing(Comparator.comparing(Team::wins).reversed())
                .thenComparing(Comparator.comparing(RoundRobinPointsStandingsEngine::difference).reversed())
                .thenComparing(Team::losses)
                .thenComparing(Team::number)
                .thenComparing(Team::id);
    }

    public static List<Ranked> rank(List<Team> teams) {
        var ordered = teams.stream().sorted(order()).toList();
        return IntStream.range(0, ordered.size())
                .mapToObj(position -> new Ranked(ordered.get(position).index(), position + 1)).toList();
    }

    private static Integer difference(Team team) {
        return team.pointsScored() - team.pointsConceded();
    }
}
