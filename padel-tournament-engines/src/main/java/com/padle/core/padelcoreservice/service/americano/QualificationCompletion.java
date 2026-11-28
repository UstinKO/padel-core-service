package com.padle.core.padelcoreservice.service.americano;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Ищет полный простой граф оставшихся встреч с заданными степенями команд. */
final class QualificationCompletion {
    record Pair(long first, long second) {
        static Pair of(long first, long second) {
            return new Pair(Math.min(first, second), Math.max(first, second));
        }
    }

    static Optional<List<Pair>> complete(List<Long> teams, int[] remaining, Set<Pair> played) {
        if (teams.size() != remaining.length || Arrays.stream(remaining).anyMatch(d -> d < 0 || d > 2)) {
            return Optional.empty();
        }
        var forbidden = new boolean[teams.size()][teams.size()];
        for (int i = 0; i < teams.size(); i++) {
            for (int j = 0; j < teams.size(); j++) {
                forbidden[i][j] = i == j || played.contains(Pair.of(teams.get(i), teams.get(j)));
            }
        }
        return solve(teams, remaining.clone(), forbidden, new HashSet<>());
    }

    private static Optional<List<Pair>> solve(List<Long> ids, int[] degrees, boolean[][] forbidden,
                                               Set<String> failed) {
        int sum = Arrays.stream(degrees).sum();
        if (sum == 0) return Optional.of(List.of());
        if (sum % 2 != 0) return Optional.empty();
        String key = Arrays.toString(degrees);
        if (failed.contains(key)) return Optional.empty();
        int selected = -1;
        int slack = Integer.MAX_VALUE;
        for (int i = 0; i < degrees.length; i++) {
            if (degrees[i] == 0) continue;
            int available = 0;
            for (int j = 0; j < degrees.length; j++) {
                if (degrees[j] > 0 && !forbidden[i][j]) available++;
            }
            if (available < degrees[i]) return Optional.empty();
            if (available - degrees[i] < slack) {
                selected = i;
                slack = available - degrees[i];
            }
        }
        int count = degrees[selected];
        for (int first = 0; first < degrees.length; first++) {
            if (degrees[first] == 0 || forbidden[selected][first]) continue;
            int last = count == 1 ? first + 1 : degrees.length;
            for (int second = count == 1 ? first : first + 1; second < last; second++) {
                if (count == 2 && (degrees[second] == 0 || forbidden[selected][second])) continue;
                var rest = degrees.clone();
                rest[selected] = 0;
                rest[first]--;
                if (count == 2) rest[second]--;
                var tail = solve(ids, rest, forbidden, failed);
                if (tail.isPresent()) {
                    var result = new ArrayList<>(tail.orElseThrow());
                    result.add(Pair.of(ids.get(selected), ids.get(first)));
                    if (count == 2) result.add(Pair.of(ids.get(selected), ids.get(second)));
                    return Optional.of(List.copyOf(result));
                }
            }
        }
        failed.add(key);
        return Optional.empty();
    }
}
