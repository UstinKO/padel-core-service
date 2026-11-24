package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.service.americano.RoundRobinSetStandingsEngine.Match;
import com.padle.core.padelcoreservice.service.americano.RoundRobinSetStandingsEngine.Ranked;
import com.padle.core.padelcoreservice.service.americano.RoundRobinSetStandingsEngine.Team;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundRobinSetStandingsEngineTest {

    @Test
    void winsPrecedeAllOtherCriteria() {
        var a = team(10, 1, 1, 3, -20);
        var b = team(20, 2, 2, 2, 20);

        assertThat(rank(List.of(b, a), List.of(new Match(2L, 1L, 6, 0))))
                .containsExactly(new Ranked(10, 1), new Ranked(20, 2));
    }

    @Test
    void overallGameDifferencePrecedesHeadToHead() {
        var a = team(10, 1, 1, 2, 2);
        var b = team(20, 2, 2, 2, 1);

        assertThat(rank(List.of(b, a), List.of(new Match(2L, 1L, 6, 0))))
                .containsExactly(new Ranked(10, 1), new Ranked(20, 2));
    }

    @Test
    void headToHeadWinnerPrecedesTheLowerTeamNumber() {
        var a = team(10, 1, 1, 2, 0);
        var b = team(20, 2, 2, 2, 0);

        assertThat(rank(List.of(a, b), List.of(new Match(2L, 1L, 6, 5))))
                .containsExactly(new Ranked(20, 1), new Ranked(10, 2));
    }

    @Test
    void matchesOutsideTheTiedGroupAreExcluded() {
        var a = team(10, 1, 1, 2, 0);
        var b = team(20, 2, 2, 2, 0);
        var other = team(30, 3, 3, 1, 0);

        assertThat(rank(List.of(a, other, b), List.of(new Match(1L, 3L, 6, 0),
                new Match(3L, 2L, 6, 0), new Match(2L, 1L, 6, 5))))
                .containsExactly(new Ranked(20, 1), new Ranked(10, 2), new Ranked(30, 3));
    }

    @Test
    void threeWayCycleUsesMiniTableDifference() {
        var teams = List.of(team(0, 1, 1, 2, 0), team(1, 2, 2, 2, 0), team(2, 3, 3, 2, 0));

        assertThat(rank(teams, List.of(new Match(1L, 2L, 6, 0), new Match(2L, 3L, 6, 4),
                new Match(3L, 1L, 6, 5))))
                .containsExactly(new Ranked(0, 1), new Ranked(2, 2), new Ranked(1, 3));
    }

    @Test
    void miniTableWinsPrecedeMiniTableDifference() {
        var teams = List.of(team(0, 1, 1, 2, 0), team(1, 2, 2, 2, 0), team(2, 3, 3, 2, 0));

        assertThat(rank(teams, List.of(new Match(1L, 2L, 6, 5), new Match(1L, 3L, 6, 5),
                new Match(2L, 3L, 6, 0))))
                .containsExactly(new Ranked(0, 1), new Ranked(1, 2), new Ranked(2, 3));
    }

    @Test
    void equalSportingResultsSharePlacesAndLeaveAGapAfterTheGroup() {
        var teams = List.of(team(5, 40, 2, 1, 0), team(6, 30, 1, 1, 0),
                team(7, 20, 1, 1, 0), team(8, 50, 3, 0, 0));

        assertThat(rank(teams, List.of())).containsExactly(new Ranked(7, 1), new Ranked(6, 1),
                new Ranked(5, 1), new Ranked(8, 4));
    }

    @Test
    void aBalancedCycleKeepsASharedPlace() {
        var teams = List.of(team(0, 1, 1, 1, 0), team(1, 2, 2, 1, 0), team(2, 3, 3, 1, 0));

        assertThat(rank(teams, List.of(new Match(1L, 2L, 6, 4), new Match(2L, 3L, 6, 4),
                new Match(3L, 1L, 6, 4))))
                .containsExactly(new Ranked(0, 1), new Ranked(1, 1), new Ranked(2, 1));
    }

    @Test
    void emptyFieldDoesNotReadMatches() {
        assertThat(rank(List.of(), null)).isEmpty();
    }

    @Test
    void aSingleTeamDoesNotNeedANumberAndKeepsItsSourceIndex() {
        assertThat(rank(List.of(new Team(99, 1L, null, 0, 0)), List.of()))
                .containsExactly(new Ranked(99, 1));
    }

    @Test
    void doesNotMutateInputs() {
        var a = team(0, 1, 1, 2, 0);
        var b = team(1, 2, 2, 2, 0);
        var teams = List.of(a, b);
        var matches = List.of(new Match(2L, 1L, 6, 4));

        assertThat(rank(teams, matches)).containsExactly(new Ranked(1, 1), new Ranked(0, 2));
        assertThat(teams).containsExactly(a, b);
        assertThat(matches).containsExactly(new Match(2L, 1L, 6, 4));
    }

    @Test
    void duplicateIdsKeepTheExistingFailure() {
        assertThatThrownBy(() -> rank(List.of(team(0, 1, 1, 1, 0), team(1, 1, 2, 1, 0)), List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    private List<Ranked> rank(List<Team> teams, List<Match> matches) {
        return RoundRobinSetStandingsEngine.rank(teams, matches);
    }

    private Team team(int index, long id, int number, int wins, int difference) {
        return new Team(index, id, number, wins, difference);
    }
}
