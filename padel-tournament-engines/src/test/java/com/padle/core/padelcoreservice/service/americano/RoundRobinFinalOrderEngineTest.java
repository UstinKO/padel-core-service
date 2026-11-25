package com.padle.core.padelcoreservice.service.americano;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundRobinFinalOrderEngineTest {
    @Test
    void originalIndexesAreIndependentOfIdAndPlace() {
        var rows = List.of(new RoundRobinFinalOrderEngine.Standing(7, 100L, 1),
                new RoundRobinFinalOrderEngine.Standing(3, 200L, 1),
                new RoundRobinFinalOrderEngine.Standing(9, 300L, 3));
        assertThat(RoundRobinFinalOrderEngine.rank(rows, List.of())).containsExactly(
                new RoundRobinFinalOrderEngine.Ranked(7, 1), new RoundRobinFinalOrderEngine.Ranked(3, 1),
                new RoundRobinFinalOrderEngine.Ranked(9, 3));
    }

    @Test
    void finalAndBronzeFillTheirSlots() {
        assertOrder(RoundRobinFinalOrderEngine.rank(circle(1, 2, 3, 4, 5, 6), List.of(
                match(5L, 6L, 6, 4, true, 1), match(3L, 4L, 4, 6, true, 3))),
                new Integer[]{4, 5, 3, 2, 0, 1}, 1, 2, 3, 4, 5, 6);
    }

    @Test
    void remainingSharedPlacesFollowAvailableSlots() {
        assertOrder(RoundRobinFinalOrderEngine.rank(circle(1, 1, 3, 3, 5, 6),
                List.of(match(6L, 5L, 6, 4, true, 3))), new Integer[]{0, 1, 5, 4, 2, 3}, 1, 1, 3, 4, 5, 5);
    }

    @Test
    void pendingAndUnplacedMatchesIgnoreNullScores() {
        assertOrder(RoundRobinFinalOrderEngine.rank(circle(1, 2), List.of(
                match(null, null, null, null, false, 1), match(null, null, null, null, true, null))),
                new Integer[]{0, 1}, 1, 2);
    }

    @Test
    void internalDrawUsesSecondSide() {
        assertOrder(RoundRobinFinalOrderEngine.rank(circle(1, 2), List.of(match(1L, 2L, 4, 4, true, 1))),
                new Integer[]{1, 0}, 1, 2);
    }

    @Test
    void laterAssignmentOverwritesSameSlots() {
        assertOrder(RoundRobinFinalOrderEngine.rank(circle(1, 2, 3, 4), List.of(
                match(3L, 4L, 6, 4, true, 1), match(3L, 4L, 4, 6, true, 1))),
                new Integer[]{3, 2, 0, 1}, 1, 2, 3, 4);
    }

    @Test
    void internalOverlapsKeepAssignmentOrder() {
        assertOrder(RoundRobinFinalOrderEngine.rank(circle(1, 2, 3, 4), List.of(
                match(1L, 2L, 6, 4, true, 1), match(3L, 4L, 6, 4, true, 2))),
                new Integer[]{0, 2, 3, 1}, 1, 2, 3, 4);
    }

    @Test
    void emptyCircleDoesNotSkipScoreProcessing() {
        assertThat(RoundRobinFinalOrderEngine.rank(List.of(), List.of())).isEmpty();
        assertThat(RoundRobinFinalOrderEngine.rank(List.of(), List.of(match(1L, 2L, 6, 4, true, 1)))).isEmpty();
        assertThatThrownBy(() -> RoundRobinFinalOrderEngine.rank(List.of(), List.of(match(1L, 2L, null, 4, true, 1))))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void duplicateIdsKeepMapFailure() {
        var rows = List.of(new RoundRobinFinalOrderEngine.Standing(0, 1L, 1),
                new RoundRobinFinalOrderEngine.Standing(1, 1L, 2));
        assertThatThrownBy(() -> RoundRobinFinalOrderEngine.rank(rows, List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownAssignedTeamKeepsSlotFailure() {
        assertThatThrownBy(() -> RoundRobinFinalOrderEngine.rank(circle(1, 2), List.of(match(99L, 2L, 6, 4, true, 1))))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void completedPlacedRowRequiresScores() {
        assertThatThrownBy(() -> RoundRobinFinalOrderEngine.rank(circle(1, 2), List.of(match(1L, 2L, null, 4, true, 1))))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5})
    void invalidInternalPlacementKeepsMissingSlotFailure(int place) {
        assertThatThrownBy(() -> RoundRobinFinalOrderEngine.rank(circle(1, 2, 3, 4), List.of(match(1L, 2L, 6, 4, true, place))))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullableIdIsNotNormalizedAndResultIsImmutable() {
        var input = List.of(new RoundRobinFinalOrderEngine.Standing(8, null, 1));
        var result = RoundRobinFinalOrderEngine.rank(input, List.of());
        assertThat(result).containsExactly(new RoundRobinFinalOrderEngine.Ranked(8, 1));
        assertThat(input).containsExactly(new RoundRobinFinalOrderEngine.Standing(8, null, 1));
        assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    private List<RoundRobinFinalOrderEngine.Standing> circle(int... places) {
        return IntStream.range(0, places.length)
                .mapToObj(index -> new RoundRobinFinalOrderEngine.Standing(index, index + 1L, places[index])).toList();
    }

    private RoundRobinFinalOrderEngine.Match match(Long first, Long second, Integer firstScore,
                                                  Integer secondScore, boolean completed, Integer place) {
        return new RoundRobinFinalOrderEngine.Match(first, second, firstScore, secondScore, completed, place);
    }

    private void assertOrder(List<RoundRobinFinalOrderEngine.Ranked> result, Integer[] indexes, Integer... places) {
        assertThat(result).extracting(RoundRobinFinalOrderEngine.Ranked::index).containsExactly(indexes);
        assertThat(result).extracting(RoundRobinFinalOrderEngine.Ranked::position).containsExactly(places);
    }
}
