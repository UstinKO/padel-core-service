package com.padle.core.padelcoreservice.service.americano;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Назначенные места занимают свои слоты; оставшиеся строки сохраняют круговой порядок и общие места. */
public final class RoundRobinFinalOrderEngine {
    private RoundRobinFinalOrderEngine() {
    }

    public record Standing(int index, Long teamId, int position) {
    }

    public record Match(Long firstId, Long secondId, Integer firstScore, Integer secondScore,
                        boolean completed, Integer placementStart) {
    }

    public record Ranked(int index, int position) {
    }

    public static List<Ranked> rank(List<Standing> circle, List<Match> extra) {
        Map<Integer, Long> assigned = new HashMap<>();
        extra.stream().filter(Match::completed).filter(match -> match.placementStart() != null)
                .forEach(match -> {
                    boolean firstWon = match.firstScore() > match.secondScore();
                    assigned.put(match.placementStart(), firstWon ? match.firstId() : match.secondId());
                    assigned.put(match.placementStart() + 1, firstWon ? match.secondId() : match.firstId());
                });
        var remaining = circle.stream().filter(team -> !assigned.containsValue(team.teamId())).toList();
        var slots = IntStream.rangeClosed(1, circle.size()).filter(position -> !assigned.containsKey(position)).boxed().toList();
        var tiedPlaces = IntStream.range(0, remaining.size()).boxed().collect(Collectors.toMap(
                index -> remaining.get(index).position(), slots::get, Math::min));
        var bySlot = IntStream.range(0, remaining.size()).boxed().collect(Collectors.toMap(slots::get, remaining::get));
        var teams = circle.stream().collect(Collectors.toMap(Standing::teamId, Function.identity()));
        return IntStream.rangeClosed(1, circle.size()).mapToObj(position -> {
            var team = assigned.containsKey(position) ? teams.get(assigned.get(position)) : bySlot.get(position);
            int place = assigned.containsKey(position) ? position : tiedPlaces.get(bySlot.get(position).position());
            return new Ranked(team.index(), place);
        }).toList();
    }
}
