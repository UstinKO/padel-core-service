package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundRobinPointsStandingsAdapterTest {

    @ParameterizedTest
    @MethodSource("priorities")
    void preservesEveryComparisonCriterion(AmericanoTeam first, AmericanoTeam second) {
        var order = RoundRobinRules.order(RoundRobinScoringMode.POINTS);
        assertThat(order.compare(first, second)).isNegative();
        assertThat(order.compare(second, first)).isPositive();
        assertThat(order.compare(first, first)).isZero();
        assertThat(RoundRobinRules.order(null).compare(first, second)).isNegative();
        assertRanking(List.of(second, first), first, second);
    }

    static Stream<Arguments> priorities() {
        return Stream.of(
                Arguments.of(team(9, 9, 12, 0, 0, 99, 99), team(1, 1, 11, 9, 99, 0, 0)),
                Arguments.of(team(9, 9, 12, 2, 0, 99, 99), team(1, 1, 12, 1, 99, 0, 0)),
                Arguments.of(team(9, 9, 12, 1, 4, 2, 99), team(1, 1, 12, 1, 30, 29, 0)),
                Arguments.of(team(9, 9, 12, 1, 4, 2, 0), team(1, 1, 12, 1, 4, 2, 1)),
                Arguments.of(team(9, 1, 12, 1, 4, 2, 0), team(1, 2, 12, 1, 4, 2, 0)),
                Arguments.of(team(1, 1, 12, 1, 4, 2, 0), team(2, 1, 12, 1, 4, 2, 0)));
    }

    @Test
    void givesSequentialPlacesForSportingTies() {
        var first = team(7, 1, 12, 1, 4, 2, 0);
        var second = team(8, 2, 12, 1, 4, 2, 0);
        var third = team(9, 3, 12, 1, 4, 2, 0);
        assertRanking(List.of(third, second, first), first, second, third);
    }

    @Test
    void keepsStableRowsWithDuplicateIdsAndNumbers() {
        var first = team(1, 1, 12, 1, 4, 2, 0);
        var second = team(1, 1, 12, 1, 4, 2, 0);
        first.setGamesWon(99);
        second.setGamesLost(99);
        assertThat(RoundRobinRules.order(RoundRobinScoringMode.POINTS).compare(first, second)).isZero();
        assertRanking(List.of(second, first), second, first);
        assertRanking(List.of(first, second), first, second);
    }

    @Test
    void ignoresMatchesForEmptyInput() {
        assertRanking(List.of());
    }

    @Test
    void doesNotCompareSingletonNullableFields() {
        var team = AmericanoTeam.builder().id(null).teamNumber(null).totalScore(null)
                .matchesWon(null).pointsScored(null).pointsConceded(null).matchesLost(null).build();
        assertRanking(List.of(team), team);
    }

    @Test
    void preservesSingletonNullRow() {
        assertRanking(Arrays.asList((AmericanoTeam) null), (AmericanoTeam) null);
    }

    @Test
    void skipsNullableLaterCriteriaWhenScoreDiffers() {
        var first = team(1, 1, 12, 0, 0, 0, 0);
        var second = team(2, 2, 11, 0, 0, 0, 0);
        for (var team : List.of(first, second)) {
            team.setMatchesWon(null);
            team.setPointsScored(null);
            team.setPointsConceded(null);
            team.setMatchesLost(null);
            team.setTeamNumber(null);
            team.setId(null);
        }
        assertRanking(List.of(second, first), first, second);
        assertThat(RoundRobinRules.order(null).compare(first, second)).isNegative();
    }

    @Test
    void skipsNullableDifferenceWhenWinsDiffer() {
        var first = team(1, 1, 12, 2, 0, 0, 0);
        var second = team(2, 2, 12, 1, 0, 0, 0);
        first.setPointsScored(null);
        second.setPointsConceded(null);
        assertRanking(List.of(second, first), first, second);
    }

    @Test
    void stillRejectsNullWhenItsCriterionIsReached() {
        var first = team(1, 1, 12, 1, 0, 0, 0);
        var second = team(2, 2, 12, 1, 0, 0, 0);
        first.setPointsScored(null);
        assertThatThrownBy(() -> RoundRobinRules.order(RoundRobinScoringMode.POINTS).compare(first, second))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RoundRobinStandings.rank(List.of(first, second), null, RoundRobinScoringMode.POINTS))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void comparesExtremeScoresWithoutComparatorSubtraction() {
        var first = team(1, 1, Integer.MAX_VALUE, 0, 0, 0, 0);
        var second = team(2, 2, Integer.MIN_VALUE, 0, 0, 0, 0);
        assertRanking(List.of(second, first), first, second);
    }

    @Test
    void preservesExistingIntegerDifferenceArithmetic() {
        var first = team(1, 1, 12, 1, Integer.MIN_VALUE, 1, 0);
        var second = team(2, 2, 12, 1, 0, 0, 0);
        assertThat(first.getPointDifference()).isEqualTo(Integer.MAX_VALUE);
        assertRanking(List.of(second, first), first, second);
    }

    @Test
    void keepsOriginalObjectsFieldsAndInputOrder() {
        var first = team(1, 1, 12, 1, 8, 4, 0);
        var second = team(2, 2, 11, 0, 3, 8, 1);
        first.setCurrentPosition(9);
        first.setMatchesPlayed(7);
        var input = List.of(second, first);
        assertRanking(input, first, second);
        assertThat(input.get(0)).isSameAs(second);
        assertThat(input.get(1)).isSameAs(first);
        assertThat(first).extracting(AmericanoTeam::getTotalScore, AmericanoTeam::getMatchesWon,
                AmericanoTeam::getPointsScored, AmericanoTeam::getPointsConceded,
                AmericanoTeam::getCurrentPosition, AmericanoTeam::getMatchesPlayed)
                .containsExactly(12, 1, 8, 4, 9, 7);
    }

    private void assertRanking(List<AmericanoTeam> input, AmericanoTeam... expected) {
        var ranked = RoundRobinStandings.rank(input, null, RoundRobinScoringMode.POINTS);
        assertThat(ranked).hasSize(expected.length);
        for (int index = 0; index < expected.length; index++) {
            assertThat(ranked.get(index).team()).isSameAs(expected[index]);
            assertThat(ranked.get(index).position()).isEqualTo(index + 1);
        }
    }

    private static AmericanoTeam team(long id, int number, int score, int wins, int scored, int conceded, int losses) {
        return AmericanoTeam.builder().id(id).teamNumber(number).totalScore(score).matchesWon(wins)
                .pointsScored(scored).pointsConceded(conceded).matchesLost(losses).build();
    }
}
