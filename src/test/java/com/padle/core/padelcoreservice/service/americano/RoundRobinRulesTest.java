package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.americano.*;
import com.padle.core.padelcoreservice.model.enums.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class RoundRobinRulesTest {
    @Test
    void existingGeneratorSchedulesEveryOpponentOnceForEvenAndOddFields() {
        var service = new TeamAmericanoService(null, null, null, null, null, null, null);
        for (int count = 2; count <= 16; count++) {
            var teams = IntStream.range(0, count).mapToObj(i -> team(i + 1)).toList();
            List<long[]> pairs = ReflectionTestUtils.invokeMethod(service, "circleMethodPairs", teams);
            assertThat(pairs).hasSize(count * (count - 1) / 2);
            for (int courts = 1; courts <= 5; courts++) {
                List<List<long[]>> rounds = ReflectionTestUtils.invokeMethod(service, "distributeIntoRounds", pairs, count, courts);
                var seen = new HashSet<String>();
                for (var round : rounds) {
                    assertThat(round.size()).isBetween(1, courts);
                    var busy = new HashSet<Long>();
                    for (var pair : round) {
                        assertThat(pair[0]).isNotEqualTo(pair[1]);
                        assertThat(busy.add(pair[0])).isTrue();
                        assertThat(busy.add(pair[1])).isTrue();
                        assertThat(seen.add(Math.min(pair[0], pair[1]) + ":" + Math.max(pair[0], pair[1]))).isTrue();
                    }
                }
                assertThat(seen).hasSize(count * (count - 1) / 2);
            }
        }
    }

    @Test
    void setAllowsExactlySixToZeroThroughSixToFive() {
        var match = match(RoundRobinScoringMode.SETS);
        for (int a = -1; a <= 8; a++) for (int b = -1; b <= 8; b++) {
            final int first = a, second = b;
            boolean valid = Math.max(a, b) == 6 && Math.min(a, b) >= 0 && Math.min(a, b) <= 5;
            if (valid) assertThatCode(() -> RoundRobinRules.validateScore(match, first, second)).doesNotThrowAnyException();
            else assertThatThrownBy(() -> RoundRobinRules.validateScore(match, first, second)).isInstanceOf(InvalidStateException.class);
        }
    }

    @Test
    void pointCircleAllowsDrawButPlacementNeedsWinner() {
        var match = match(RoundRobinScoringMode.POINTS);
        RoundRobinRules.validateScore(match, 16, 16);
        assertThatThrownBy(() -> RoundRobinRules.validateScore(match, 18, 15)).isInstanceOf(InvalidStateException.class);
        match.getRound().setPhase(TournamentPhase.PLACEMENT);
        match.setPlacementStart(1);
        assertThatThrownBy(() -> RoundRobinRules.validateScore(match, 16, 16)).isInstanceOf(InvalidStateException.class);
        RoundRobinRules.validateScore(match, 18, 14);
    }

    @Test
    void setsPrioritizeWinsWhilePointsPrioritizePointsAndCorrectionRevertsStats() {
        var first = team(1);
        var second = team(2);
        RoundRobinRules.apply(first, 6, 0, RoundRobinScoringMode.SETS, 1);
        RoundRobinRules.apply(second, 5, 6, RoundRobinScoringMode.SETS, 1);
        RoundRobinRules.apply(second, 5, 6, RoundRobinScoringMode.SETS, 1);
        assertThat(List.of(first, second).stream().sorted(RoundRobinRules.order(RoundRobinScoringMode.SETS)).toList()).containsExactly(first, second);
        assertThat(List.of(first, second).stream().sorted(RoundRobinRules.order(RoundRobinScoringMode.POINTS)).toList()).containsExactly(second, first);
        RoundRobinRules.apply(first, 6, 0, RoundRobinScoringMode.SETS, -1);
        assertThat(first.getMatchesPlayed()).isZero();
        assertThat(first.getGamesWon()).isZero();
        assertThat(first.getSetsWon()).isZero();
        assertThat(first.getTotalScore()).isZero();
    }

    private AmericanoTeam team(int n) {
        return AmericanoTeam.builder().id((long) n).teamNumber(n).build();
    }

    private AmericanoMatch match(RoundRobinScoringMode mode) {
        return AmericanoMatch.builder().round(AmericanoRound.builder().scoringMode(mode).pointsPerMatch(32).build()).build();
    }
}
