package com.padle.core.padelcoreservice.service.americano;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;

class PlayoffContinuationTest {
    @Test
    void checksTheWholeEightRatherThanOneLocalSwap() {
        var slots = List.of(List.of(10L), List.of(4L), List.of(8L), List.of(5L),
                List.of(3L), List.of(2L), List.of(1L), List.of(7L));
        var history = Map.of(8L, Set.of(2L), 2L, Set.of(8L));
        assertThat(PlayoffContinuation.safe(slots, Set.of(), history)).isTrue();
        assertThat(PlayoffContinuation.safe(slots, Set.of(QualificationCompletion.Pair.of(8, 2)), history)).isFalse();
        var full = Set.of(QualificationCompletion.Pair.of(10, 3), QualificationCompletion.Pair.of(4, 2),
                QualificationCompletion.Pair.of(8, 1), QualificationCompletion.Pair.of(5, 7));
        assertThat(PlayoffContinuation.safe(slots, full, history)).isTrue();
    }

    @Test
    void aSafePresentPairCanStillStrandOneFutureWinnerCombination() {
        var slots = List.of(List.of(1L), List.of(2L), List.of(3L), List.of(4L),
                List.of(5L), List.of(6L), List.of(7L), List.of(8L, 9L));
        var history = new HashMap<Long, Set<Long>>();
        for (long team : List.of(3L, 4L, 5L, 6L, 7L)) history.put(team, Set.of(8L));
        assertThat(PlayoffContinuation.safe(slots, Set.of(), history)).isTrue();
        assertThat(PlayoffContinuation.safe(slots, Set.of(QualificationCompletion.Pair.of(1, 2)), history)).isFalse();
    }

    @Test
    void globalFutureProofAgreesWithIndependentEnumerationForAllTeamCounts() {
        var random = new Random(1520);
        for (int count = 9; count <= 16; count++) {
            int direct = 16 - count;
            for (int scenario = 0; scenario < 20; scenario++) {
                var slots = new ArrayList<List<Long>>();
                for (long id = 1; id <= direct; id++) slots.add(List.of(id));
                for (long id = direct + 1; id < count; id += 2) slots.add(List.of(id, id + 1));
                var history = new HashMap<Long, Set<Long>>();
                for (long id = 1; id <= count; id++) {
                    var rivals = new HashSet<Long>();
                    for (long other = id + 1; other <= count; other++) if (random.nextInt(5) == 0) rivals.add(other);
                    history.put(id, rivals);
                }
                var ready = slots.stream().filter(s -> s.size() == 1).map(s -> s.get(0)).toList();
                var fixed = ready.size() >= 2 ? Set.of(QualificationCompletion.Pair.of(ready.get(0), ready.get(1)))
                        : Set.<QualificationCompletion.Pair>of();
                assertThat(PlayoffContinuation.safe(slots, fixed, history))
                        .isEqualTo(bruteOutcomes(slots, 0, new ArrayList<>(), fixed, history));
            }
        }
    }

    private boolean bruteOutcomes(List<List<Long>> slots, int index, List<Long> selected,
                                  Set<QualificationCompletion.Pair> fixed, Map<Long, Set<Long>> history) {
        if (index == slots.size()) {
            var remaining = new ArrayList<>(selected);
            for (var pair : fixed) {
                if (met(pair.first(), pair.second(), history)) return false;
                remaining.remove(pair.first());
                remaining.remove(pair.second());
            }
            return bruteMatching(remaining, history);
        }
        for (long id : slots.get(index)) {
            selected.add(id);
            boolean safe = bruteOutcomes(slots, index + 1, selected, fixed, history);
            selected.remove(selected.size() - 1);
            if (!safe) return false;
        }
        return true;
    }

    private boolean bruteMatching(List<Long> teams, Map<Long, Set<Long>> history) {
        if (teams.isEmpty()) return true;
        long first = teams.get(0);
        for (int i = 1; i < teams.size(); i++) {
            long second = teams.get(i);
            if (!met(first, second, history) && bruteMatching(teams.stream()
                    .filter(id -> id != first && id != second).toList(), history)) return true;
        }
        return false;
    }

    private boolean met(long first, long second, Map<Long, Set<Long>> history) {
        return history.getOrDefault(first, Set.of()).contains(second) || history.getOrDefault(second, Set.of()).contains(first);
    }
}
