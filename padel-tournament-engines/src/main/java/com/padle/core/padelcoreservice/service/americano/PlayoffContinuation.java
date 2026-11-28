package com.padle.core.padelcoreservice.service.americano;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Готовую пару можно закрепить, только если каждый вариант победителей допускает весь остаток. */
final class PlayoffContinuation {
    static boolean safe(List<List<Long>> slots, Set<QualificationCompletion.Pair> fixed,
                        Map<Long, Set<Long>> opponents) {
        if (slots.size() != 8 || slots.stream().anyMatch(List::isEmpty)) return false;
        var forbidden = new HashSet<QualificationCompletion.Pair>();
        opponents.forEach((team, rivals) -> rivals.forEach(rival ->
                forbidden.add(QualificationCompletion.Pair.of(team, rival))));
        return everyOutcome(slots, 0, new ArrayList<>(), fixed, forbidden);
    }

    private static boolean everyOutcome(List<List<Long>> slots, int index, List<Long> selected,
                                        Set<QualificationCompletion.Pair> fixed,
                                        Set<QualificationCompletion.Pair> forbidden) {
        if (index == slots.size()) {
            var remaining = new ArrayList<>(selected);
            for (var pair : fixed) {
                if (forbidden.contains(pair) || !remaining.remove(pair.first()) || !remaining.remove(pair.second())) {
                    return false;
                }
            }
            var degrees = new int[remaining.size()];
            java.util.Arrays.fill(degrees, 1);
            return QualificationCompletion.complete(remaining, degrees, forbidden).isPresent();
        }
        for (long candidate : slots.get(index)) {
            if (selected.contains(candidate)) return false;
            selected.add(candidate);
            boolean possible = everyOutcome(slots, index + 1, selected, fixed, forbidden);
            selected.remove(selected.size() - 1);
            if (!possible) return false;
        }
        return true;
    }
}
