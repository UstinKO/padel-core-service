package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundRobinStandingsAdapterTest {

    @Test
    void winsPrecedeGameDifferenceAndHeadToHead() {
        var a = team(1, 1, 3, -20);
        var b = team(2, 2, 2, 20);

        assertOrder(rank(List.of(b, a), List.of(match(b, a, 6, 0))), List.of(a, b), 1, 2);
    }

    @Test
    void overallDifferencePrecedesHeadToHead() {
        var a = team(1, 1, 2, 2);
        var b = team(2, 2, 2, 1);

        assertOrder(rank(List.of(b, a), List.of(match(b, a, 6, 0))), List.of(a, b), 1, 2);
    }

    @Test
    void miniTableIgnoresMeetingsOutsideTheTiedGroup() {
        var a = team(1, 1, 2, 0);
        var b = team(2, 2, 2, 0);
        var other = team(3, 3, 1, 0);

        var ranked = rank(List.of(a, other, b), List.of(match(a, other, 6, 0),
                match(other, b, 6, 0), match(b, a, 6, 5)));

        assertOrder(ranked, List.of(b, a, other), 1, 2, 3);
    }

    @Test
    void unrelatedCompletedMatchesDoNotChangeTheMiniTable() {
        var a = team(1, 1, 2, 0);
        var b = team(2, 2, 2, 0);
        var unrelated = match(team(90, 90, 0, 0), team(91, 91, 0, 0), 6, 0);

        assertOrder(rank(List.of(a, b), List.of(unrelated, match(b, a, 6, 5))), List.of(b, a), 1, 2);
    }

    @Test
    void equalSportingResultsSharePlacesWhileNumberAndIdOnlyOrderRows() {
        var a = team(40, 2, 1, 0);
        var b = team(30, 1, 1, 0);
        var c = team(20, 1, 1, 0);
        var last = team(50, 3, 0, 0);

        assertOrder(rank(List.of(a, last, b, c), List.of()), List.of(c, b, a, last), 1, 1, 1, 4);
    }

    @Test
    void ignoresPlacementPendingAndIncompleteScores() {
        var a = team(1, 1, 2, 0);
        var b = team(2, 2, 2, 0);
        var placement = match(a, b, 6, 0);
        placement.getRound().setPhase(TournamentPhase.PLACEMENT);
        var pending = AmericanoMatch.builder().status(AmericanoRoundStatus.PENDING).build();
        var missingFirst = match(a, b, 6, 0);
        missingFirst.setTeam1Score(null);
        var missingSecond = match(a, b, 6, 0);
        missingSecond.setTeam2Score(null);

        assertOrder(rank(List.of(a, b), List.of(placement, pending, missingFirst, missingSecond,
                match(b, a, 6, 5))), List.of(b, a), 1, 2);
    }

    @Test
    void fourTiedTeamsUseMiniTableWinsBeforeMiniDifference() {
        var a = team(1, 1, 2, 0);
        var b = team(2, 2, 2, 0);
        var c = team(3, 3, 2, 0);
        var d = team(4, 4, 2, 0);
        var matches = List.of(match(a, b, 6, 5), match(a, c, 6, 5), match(a, d, 6, 5),
                match(b, c, 6, 0), match(b, d, 6, 0), match(c, d, 6, 0));

        assertOrder(rank(List.of(d, c, b, a), matches), List.of(a, b, c, d), 1, 2, 3, 4);
    }

    @Test
    void returnsOriginalEntitiesWithoutChangingInputOrderOrSavedPositions() {
        var a = team(1, 1, 1, 0);
        var b = team(2, 2, 1, 0);
        a.setCurrentPosition(99);
        b.setCurrentPosition(98);
        var teams = new ArrayList<>(List.of(a, b));
        var matches = new ArrayList<>(List.of(match(b, a, 6, 4)));

        assertOrder(rank(teams, matches), List.of(b, a), 1, 2);
        assertThat(teams.get(0)).isSameAs(a);
        assertThat(teams.get(1)).isSameAs(b);
        assertThat(a.getCurrentPosition()).isEqualTo(99);
        assertThat(b.getCurrentPosition()).isEqualTo(98);
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getTeam1Score()).isEqualTo(6);
    }

    @Test
    void setsEmptyFieldDoesNotReadMatches() {
        assertThat(rank(List.of(), null)).isEmpty();
    }

    @Test
    void singleTeamDoesNotNeedATeamNumberForComparison() {
        var only = team(1, 1, 0, 0);
        only.setTeamNumber(null);

        assertOrder(rank(List.of(only), List.of()), List.of(only), 1);
    }

    @Test
    void pointsIgnoreMatchesAndKeepSequentialPlacesForEqualResults() {
        var a = team(1, 1, 1, 0);
        var b = team(2, 2, 1, 0);
        a.setTotalScore(12);
        b.setTotalScore(12);

        assertOrder(RoundRobinStandings.rank(List.of(b, a), null, RoundRobinScoringMode.POINTS),
                List.of(a, b), 1, 2);
    }

    @Test
    void pointsKeepTwoDistinctEntitiesWithTheSameId() {
        var a = team(1, 1, 1, 0);
        var b = team(1, 2, 1, 0);
        a.setTotalScore(12);
        b.setTotalScore(15);

        assertOrder(RoundRobinStandings.rank(List.of(a, b), null, RoundRobinScoringMode.POINTS),
                List.of(b, a), 1, 2);
    }

    @Test
    void setsKeepTheirExistingRejectionOfDuplicateIds() {
        var a = team(1, 1, 1, 0);
        var b = team(1, 2, 1, 0);

        assertThatThrownBy(() -> rank(List.of(a, b), List.of())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void nullModeKeepsTheExistingSetsPathOfThisJavaApi() {
        var a = team(1, 1, 1, 0);
        var b = team(2, 2, 1, 0);

        assertOrder(RoundRobinStandings.rank(List.of(a, b), List.of(match(b, a, 6, 4)), null),
                List.of(b, a), 1, 2);
    }

    private List<RoundRobinStandings.Ranked> rank(List<AmericanoTeam> teams, List<AmericanoMatch> matches) {
        return RoundRobinStandings.rank(teams, matches, RoundRobinScoringMode.SETS);
    }

    private void assertOrder(List<RoundRobinStandings.Ranked> ranked, List<AmericanoTeam> teams, Integer... places) {
        assertThat(ranked).hasSameSizeAs(teams);
        IntStream.range(0, teams.size())
                .forEach(index -> assertThat(ranked.get(index).team()).isSameAs(teams.get(index)));
        assertThat(ranked.stream().map(RoundRobinStandings.Ranked::position).toList()).containsExactly(places);
    }

    private AmericanoTeam team(long id, int number, int wins, int difference) {
        return AmericanoTeam.builder().id(id).teamNumber(number).matchesWon(wins)
                .gamesWon(20).gamesLost(20 - difference).build();
    }

    private AmericanoMatch match(AmericanoTeam first, AmericanoTeam second, int firstScore, int secondScore) {
        return AmericanoMatch.builder().round(AmericanoRound.builder().build())
                .team1Id(first.getId()).team2Id(second.getId()).team1Score(firstScore).team2Score(secondScore)
                .status(AmericanoRoundStatus.COMPLETED).build();
    }
}
