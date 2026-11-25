package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.model.americano.*;
import com.padle.core.padelcoreservice.model.enums.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RoundRobinStandingsTest {
    @Test
    void directWinnerPrecedesOpponentWithMoreGamesWonAndTeamNumberIsNotATiebreak() {
        var first = team(2, 2, 0, 14);
        var second = team(1, 2, 0, 16);
        var ranked = rank(List.of(second, first), List.of(result(first, second, 6, 4)));
        assertThat(ranked.stream().map(r -> r.team().getId())).containsExactly(first.getId(), second.getId());
        assertThat(ranked.stream().map(RoundRobinStandings.Ranked::position)).containsExactly(1, 2);
    }

    @Test
    void threeWayCycleUsesOnlyMiniTableDifferenceAndExcludesFinalAndPending() {
        var a = team(1, 2, 0, 20); var b = team(2, 2, 0, 21); var c = team(3, 2, 0, 22);
        var finalMatch = result(b, a, 6, 0);
        finalMatch.getRound().setPhase(TournamentPhase.PLACEMENT);
        var pending = result(b, c, 6, 0); pending.setStatus(AmericanoRoundStatus.PENDING);
        var ranked = rank(List.of(b, c, a), List.of(result(a, b, 6, 0), result(b, c, 6, 4),
                result(c, a, 6, 5), finalMatch, pending));
        assertThat(ranked.stream().map(r -> r.team().getId())).containsExactly(1L, 3L, 2L);
    }

    @Test
    void miniTableWinsComeBeforeMiniDifference() {
        var a = team(1, 2, 0, 20); var b = team(2, 2, 0, 20); var c = team(3, 2, 0, 20);
        var ranked = rank(List.of(c, b, a), List.of(result(a, b, 6, 5), result(a, c, 6, 5), result(b, c, 6, 0)));
        assertThat(ranked.stream().map(r -> r.team().getId())).containsExactly(1L, 2L, 3L);
    }

    @Test
    void completeCycleSharesPlaceAndNextTeamStartsAfterAllTiedTeams() {
        var a = team(1, 1, 0, 20); var b = team(2, 1, 0, 21); var c = team(3, 1, 0, 22);
        var last = team(4, 0, -3, 20);
        var ranked = rank(List.of(c, b, last, a), List.of(result(a, b, 6, 4), result(b, c, 6, 4), result(c, a, 6, 4)));
        assertThat(ranked.stream().map(RoundRobinStandings.Ranked::position)).containsExactly(1, 1, 1, 4);
        assertThat(RoundRobinRules.finalOrder(ranked, List.of()).stream().map(r -> r.position()))
                .containsExactly(1, 1, 1, 4);
    }

    @Test
    void manualFinalOverridesPlacesAndRemainingTiedTeamsStillShareAPlace() {
        var a = team(1, 0, 0, 0); var b = team(2, 0, 0, 0);
        var c = team(3, 0, 0, 0); var d = team(4, 0, 0, 0);
        var ranked = rank(List.of(a, b, c, d), List.of());
        var finalMatch = result(c, d, 6, 5); finalMatch.setPlacementStart(1);
        finalMatch.getRound().setPhase(TournamentPhase.PLACEMENT);
        var finished = RoundRobinRules.finalOrder(ranked, List.of(finalMatch));
        assertThat(finished.stream().map(r -> r.teamId())).containsExactly(3L, 4L, 1L, 2L);
        assertThat(finished.stream().map(r -> r.position())).containsExactly(1, 2, 3, 3);
    }

    @Test
    void pointsKeepTheirExistingSequentialOrderAndIgnoreHeadToHead() {
        var a = team(1, 1, 0, 10); var b = team(2, 1, 0, 10);
        a.setTotalScore(12); b.setTotalScore(15);
        var ranked = RoundRobinStandings.rank(List.of(a, b), List.of(result(a, b, 6, 0)), RoundRobinScoringMode.POINTS);
        assertThat(ranked.stream().map(r -> r.team().getId())).containsExactly(2L, 1L);
        assertThat(ranked.stream().map(RoundRobinStandings.Ranked::position)).containsExactly(1, 2);
    }

    private List<RoundRobinStandings.Ranked> rank(List<AmericanoTeam> teams, List<AmericanoMatch> matches) {
        return RoundRobinStandings.rank(teams, matches, RoundRobinScoringMode.SETS);
    }

    private AmericanoTeam team(int n, int wins, int difference, int games) {
        var team = AmericanoTeam.builder().id((long) n).teamNumber(n).matchesWon(wins).gamesWon(games).build();
        team.setGamesLost(games - difference);
        return team;
    }

    private AmericanoMatch result(AmericanoTeam first, AmericanoTeam second, int score1, int score2) {
        return AmericanoMatch.builder().round(AmericanoRound.builder().scoringMode(RoundRobinScoringMode.SETS).build())
                .team1Id(first.getId()).team2Id(second.getId()).team1Score(score1).team2Score(score2)
                .status(AmericanoRoundStatus.COMPLETED).build();
    }
}
