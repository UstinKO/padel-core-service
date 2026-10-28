package com.padle.core.padelcoreservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.padle.core.padelcoreservice.service.KingOfCourtPairingEngine.PartnershipKey;
import static com.padle.core.padelcoreservice.service.KingOfCourtPairingEngine.PartnershipStats;
import static com.padle.core.padelcoreservice.service.KingOfCourtPairingEngine.PlayedCourt;
import static com.padle.core.padelcoreservice.service.KingOfCourtPairingEngine.buildPartnershipHistory;
import static com.padle.core.padelcoreservice.service.KingOfCourtPairingEngine.possibleTeamCombinations;
import static com.padle.core.padelcoreservice.service.KingOfCourtPairingEngine.selectBestTeams;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KingOfCourtPairingEngineTest {

    private static final long A = 1L;
    private static final long B = 2L;
    private static final long C = 3L;
    private static final long D = 4L;
    private static final long E = 5L;
    private static final long F = 6L;

    @Test
    void possibleTeamCombinations_rejectsGroupsOfOtherSizeThanFour() {
        assertThatThrownBy(() -> possibleTeamCombinations(List.of(A, B, C)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void possibleTeamCombinations_returnsAllThreeUniquePartitions() {
        List<List<List<Long>>> combos = possibleTeamCombinations(List.of(A, B, C, D));

        assertThat(combos).hasSize(3);
        for (List<List<Long>> combo : combos) {
            Set<Long> covered = combo.stream().flatMap(List::stream).collect(Collectors.toSet());
            assertThat(covered).containsExactlyInAnyOrder(A, B, C, D);
        }
    }

    @Test
    void buildPartnershipHistory_mergesRepeatedPairsAndTracksLatestRound() {
        List<PlayedCourt> played = List.of(
                new PlayedCourt(1, List.of(A, B), List.of(C, D)),
                new PlayedCourt(2, List.of(A, C), List.of(B, D)),
                new PlayedCourt(5, List.of(A, B), List.of(C, D))
        );

        Map<PartnershipKey, PartnershipStats> history = buildPartnershipHistory(played);

        assertThat(history.get(PartnershipKey.of(A, B))).isEqualTo(new PartnershipStats(2, 5));
        assertThat(history.get(PartnershipKey.of(C, D))).isEqualTo(new PartnershipStats(2, 5));
        assertThat(history.get(PartnershipKey.of(A, C))).isEqualTo(new PartnershipStats(1, 2));
        assertThat(history.get(PartnershipKey.of(B, D))).isEqualTo(new PartnershipStats(1, 2));
        assertThat(history).doesNotContainKey(PartnershipKey.of(A, D));
    }

    @Test
    void selectBestTeams_clientExample_choosesOnlyCombinationWithNoSharedHistory() {
        // Раунд 10 только что завершился: A+B и E+F были партнёрами в нём; A+E — 3 раунда назад (раунд 7).
        Map<PartnershipKey, PartnershipStats> history = Map.of(
                PartnershipKey.of(A, B), new PartnershipStats(1, 10),
                PartnershipKey.of(E, F), new PartnershipStats(1, 10),
                PartnershipKey.of(A, E), new PartnershipStats(1, 7)
        );

        List<List<Long>> selected = selectBestTeams(List.of(A, B, E, F), history, 10, new Random(42));

        assertThat(selected).containsExactlyInAnyOrder(List.of(A, F), List.of(B, E));
    }

    @Test
    void selectBestTeams_tieOnPriority1And2_prefersMoreDistantPartnership() {
        // A+B — последний матч 2 раунда назад (8), A+C — 6 раундов назад (4); оба по одному совместному
        // матчу. A+D искусственно "только что" сыграли (10) — этот вариант должен быть отсеян приоритетом 1.
        Map<PartnershipKey, PartnershipStats> history = Map.of(
                PartnershipKey.of(A, B), new PartnershipStats(1, 8),
                PartnershipKey.of(A, C), new PartnershipStats(1, 4),
                PartnershipKey.of(A, D), new PartnershipStats(1, 10)
        );

        List<List<Long>> selected = selectBestTeams(List.of(A, B, C, D), history, 10, new Random(7));

        assertThat(selected).containsExactlyInAnyOrder(List.of(A, C), List.of(B, D));
    }

    @Test
    void selectBestTeams_tieOnPriority1_prefersFewerTotalSharedMatches() {
        Map<PartnershipKey, PartnershipStats> history = Map.of(
                PartnershipKey.of(A, B), new PartnershipStats(2, 5),
                PartnershipKey.of(C, D), new PartnershipStats(1, 6),
                PartnershipKey.of(B, D), new PartnershipStats(1, 7),
                PartnershipKey.of(A, D), new PartnershipStats(3, 9)
        );

        List<List<Long>> selected = selectBestTeams(List.of(A, B, C, D), history, 10, new Random(3));

        assertThat(selected).containsExactlyInAnyOrder(List.of(A, C), List.of(B, D));
    }

    @Test
    void selectBestTeams_unavoidableRepeats_stillPicksCombinationWithFewestTotal() {
        // Все три разбиения уже встречались раньше — алгоритм не должен падать, выбирает минимум.
        Map<PartnershipKey, PartnershipStats> history = Map.of(
                PartnershipKey.of(A, B), new PartnershipStats(3, 2),
                PartnershipKey.of(C, D), new PartnershipStats(3, 2),
                PartnershipKey.of(A, C), new PartnershipStats(2, 5),
                PartnershipKey.of(B, D), new PartnershipStats(2, 5),
                PartnershipKey.of(A, D), new PartnershipStats(1, 8),
                PartnershipKey.of(B, C), new PartnershipStats(1, 8)
        );

        List<List<Long>> selected = selectBestTeams(List.of(A, B, C, D), history, 10, new Random(99));

        assertThat(selected).containsExactlyInAnyOrder(List.of(A, D), List.of(B, C));
    }

    @Test
    void selectBestTeams_noHistory_randomlyCoversAllThreeCombinations() {
        Set<Set<Long>> observedFirstTeams = new HashSet<>();
        for (int seed = 0; seed < 200; seed++) {
            List<List<Long>> selected = selectBestTeams(List.of(A, B, C, D), Map.of(), 0, new Random(seed));
            observedFirstTeams.add(new HashSet<>(selected.get(0)));
        }

        assertThat(observedFirstTeams).hasSize(3);
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 12, 16, 20, 24})
    void simulatedTournament_neverPicksAvoidableImmediateRepeat_andAlwaysReturnsValidSplit(int playerCount) {
        List<Long> players = playerIds(playerCount);
        Random shuffleRandom = new Random(playerCount);
        Map<PartnershipKey, PartnershipStats> history = new HashMap<>();

        for (int round = 1; round <= 25; round++) {
            List<Long> shuffled = new ArrayList<>(players);
            Collections.shuffle(shuffled, shuffleRandom);

            List<PlayedCourt> roundResults = new ArrayList<>();
            for (int i = 0; i < shuffled.size(); i += 4) {
                List<Long> group = shuffled.subList(i, i + 4);
                List<List<Long>> selected = selectBestTeams(group, Map.copyOf(history), round - 1, new Random(round * 31 + i));

                assertThat(selected).hasSize(2);
                Set<Long> covered = new HashSet<>();
                selected.forEach(covered::addAll);
                assertThat(covered).containsExactlyInAnyOrderElementsOf(group);

                assertSelectedIsOptimalByPriority(group, selected, history, round - 1);

                roundResults.add(new PlayedCourt(round, selected.get(0), selected.get(1)));
            }

            history.putAll(buildPartnershipHistory(roundResults).entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> mergeStats(history.get(entry.getKey()), entry.getValue()))));
        }
    }

    private static PartnershipStats mergeStats(PartnershipStats previous, PartnershipStats fromThisRound) {
        if (previous == null) {
            return fromThisRound;
        }
        return new PartnershipStats(previous.timesPlayedTogether() + fromThisRound.timesPlayedTogether(),
                Math.max(previous.lastRoundNumber(), fromThisRound.lastRoundNumber()));
    }

    /** Явно пересчитывает приоритетный триплет для всех 3 комбинаций и проверяет, что выбор — лексикографический минимум. */
    private static void assertSelectedIsOptimalByPriority(List<Long> group, List<List<Long>> selected,
            Map<PartnershipKey, PartnershipStats> history, int justCompletedRoundNumber) {
        List<List<List<Long>>> combos = possibleTeamCombinations(group);
        List<int[]> triples = combos.stream().map(combo -> priorityTriple(combo, history, justCompletedRoundNumber)).toList();
        int[] selectedTriple = priorityTriple(selected, history, justCompletedRoundNumber);

        int[] bestTriple = triples.stream().reduce(selectedTriple, (a, b) -> compareTriples(a, b) <= 0 ? a : b);
        assertThat(compareTriples(selectedTriple, bestTriple)).isEqualTo(0);
    }

    private static int[] priorityTriple(List<List<Long>> combo, Map<PartnershipKey, PartnershipStats> history, int justCompletedRoundNumber) {
        int immediateRepeats = (int) combo.stream()
                .filter(team -> {
                    PartnershipStats stats = history.get(PartnershipKey.of(team.get(0), team.get(1)));
                    return stats != null && stats.lastRoundNumber() == justCompletedRoundNumber;
                })
                .count();
        int total = combo.stream()
                .mapToInt(team -> {
                    PartnershipStats stats = history.get(PartnershipKey.of(team.get(0), team.get(1)));
                    return stats == null ? 0 : stats.timesPlayedTogether();
                })
                .sum();
        int recency = combo.stream()
                .mapToInt(team -> {
                    PartnershipStats stats = history.get(PartnershipKey.of(team.get(0), team.get(1)));
                    return stats == null ? 0 : stats.lastRoundNumber();
                })
                .max().orElse(0);
        return new int[]{immediateRepeats, total, recency};
    }

    private static int compareTriples(int[] a, int[] b) {
        return Comparator.<int[]>comparingInt(t -> t[0])
                .thenComparingInt(t -> t[1])
                .thenComparingInt(t -> t[2])
                .compare(a, b);
    }

    private static List<Long> playerIds(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> (long) i).toList();
    }
}
