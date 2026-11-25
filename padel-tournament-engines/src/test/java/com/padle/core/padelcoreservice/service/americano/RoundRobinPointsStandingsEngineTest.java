package com.padle.core.padelcoreservice.service.americano;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundRobinPointsStandingsEngineTest {

    @ParameterizedTest
    @MethodSource("priorities")
    void sortsByEachCriterion(RoundRobinPointsStandingsEngine.Team first,
                             RoundRobinPointsStandingsEngine.Team second) {
        assertThat(RoundRobinPointsStandingsEngine.order().compare(first, second)).isNegative();
        assertThat(RoundRobinPointsStandingsEngine.order().compare(second, first)).isPositive();
        assertThat(RoundRobinPointsStandingsEngine.rank(List.of(second, first))).containsExactly(
                new RoundRobinPointsStandingsEngine.Ranked(first.index(), 1),
                new RoundRobinPointsStandingsEngine.Ranked(second.index(), 2));
    }

    static Stream<Arguments> priorities() {
        return Stream.of(
                Arguments.of(team(9, 9, 9, 12, 0, 0, 99, 99), team(1, 1, 1, 11, 9, 99, 0, 0)),
                Arguments.of(team(9, 9, 9, 12, 2, 0, 99, 99), team(1, 1, 1, 12, 1, 99, 0, 0)),
                Arguments.of(team(9, 9, 9, 12, 1, 4, 2, 99), team(1, 1, 1, 12, 1, 30, 29, 0)),
                Arguments.of(team(9, 9, 9, 12, 1, 4, 2, 0), team(1, 1, 1, 12, 1, 4, 2, 1)),
                Arguments.of(team(9, 9, 1, 12, 1, 4, 2, 0), team(1, 1, 2, 12, 1, 4, 2, 0)),
                Arguments.of(team(9, 1, 1, 12, 1, 4, 2, 0), team(1, 2, 1, 12, 1, 4, 2, 0)));
    }

    @Test
    void emptyInputHasNoPlaces() {
        assertThat(RoundRobinPointsStandingsEngine.rank(List.of())).isEmpty();
    }

    @Test
    void singletonNullableStatisticsNeedNoComparison() {
        var team = new RoundRobinPointsStandingsEngine.Team(8, null, null, null, null, null, null, null);
        assertThat(RoundRobinPointsStandingsEngine.rank(List.of(team)))
                .containsExactly(new RoundRobinPointsStandingsEngine.Ranked(8, 1));
    }

    @Test
    void tiesRemainStableAndHaveSequentialPlaces() {
        var first = team(8, 1, 1, 12, 1, 4, 2, 0);
        var second = team(3, 1, 1, 12, 1, 4, 2, 0);
        var input = List.of(first, second, first);
        assertThat(RoundRobinPointsStandingsEngine.order().compare(first, second)).isZero();
        assertThat(RoundRobinPointsStandingsEngine.rank(input)).containsExactly(
                new RoundRobinPointsStandingsEngine.Ranked(8, 1),
                new RoundRobinPointsStandingsEngine.Ranked(3, 2),
                new RoundRobinPointsStandingsEngine.Ranked(8, 3));
        assertThat(input).containsExactly(first, second, first);
    }

    @Test
    void earlierCriterionSkipsNullableDifference() {
        var first = new RoundRobinPointsStandingsEngine.Team(2, null, null, 9, 2, null, null, null);
        var second = new RoundRobinPointsStandingsEngine.Team(7, null, null, 9, 1, null, null, null);
        assertThat(RoundRobinPointsStandingsEngine.rank(List.of(second, first))).containsExactly(
                new RoundRobinPointsStandingsEngine.Ranked(2, 1), new RoundRobinPointsStandingsEngine.Ranked(7, 2));
    }

    @Test
    void reachedNullableDifferenceStillFails() {
        var first = new RoundRobinPointsStandingsEngine.Team(0, 1L, 1, 9, 1, null, 0, 0);
        var second = team(1, 2, 2, 9, 1, 0, 0, 0);
        assertThatThrownBy(() -> RoundRobinPointsStandingsEngine.rank(List.of(first, second)))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void extremeScoresUseNaturalIntegerOrdering() {
        var first = team(0, 1, 1, Integer.MAX_VALUE, 0, 0, 0, 0);
        var second = team(1, 2, 2, Integer.MIN_VALUE, 0, 0, 0, 0);
        assertThat(RoundRobinPointsStandingsEngine.order().compare(first, second)).isNegative();
    }

    private static RoundRobinPointsStandingsEngine.Team team(int index, long id, int number, int total,
                                                            int wins, int scored, int conceded, int losses) {
        return new RoundRobinPointsStandingsEngine.Team(index, id, number, total, wins, scored, conceded, losses);
    }
}
