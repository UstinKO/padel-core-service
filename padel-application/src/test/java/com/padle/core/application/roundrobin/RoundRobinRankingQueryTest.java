package com.padle.core.application.roundrobin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static com.padle.core.application.roundrobin.RoundRobinReadSet.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundRobinRankingQueryTest {
    @Test
    void emptyUninitializedEventKeepsDefaults() {
        var result = query(read(List.of(), List.of(), List.of(), List.of())).ranking(42L);
        assertThat(result).isEqualTo(new RoundRobinRanking(42L, "QA", 0, 0, 0, false,
                ScoringMode.POINTS, 32, 1, false, List.of(), List.of(), List.of()));
    }

    static Stream<Arguments> lifecycle() {
        return Stream.of(ScoringMode.values()).flatMap(mode -> Stream.of("initial", "circle", "pending-final", "played-final")
                .map(state -> Arguments.of(mode, state)));
    }

    @ParameterizedTest
    @MethodSource("lifecycle")
    void wireBaselineStatesRetainCircleAndFinalSemantics(ScoringMode mode, String state) {
        boolean played = !state.equals("initial");
        boolean extra = state.endsWith("final");
        boolean finalPlayed = state.equals("played-final");
        var first = team(1L, 1, statistics(mode, played, true));
        var second = team(2L, 2, statistics(mode, played, false));
        var rounds = new ArrayList<>(List.of(new Round(null, played ? Status.COMPLETED : Status.IN_PROGRESS, mode, 32, 2)));
        var matches = new ArrayList<>(List.of(match(null, played ? Status.COMPLETED : Status.IN_PROGRESS,
                played ? (mode == ScoringMode.SETS ? 6 : 18) : null,
                played ? (mode == ScoringMode.SETS ? 4 : 14) : null, null)));
        var additional = new ArrayList<Match>();
        if (extra) {
            rounds.add(new Round(Phase.PLACEMENT, finalPlayed ? Status.COMPLETED : Status.IN_PROGRESS, mode, 32, 2));
            var finalMatch = match(Phase.PLACEMENT, finalPlayed ? Status.COMPLETED : Status.IN_PROGRESS,
                    finalPlayed ? 4 : null, finalPlayed ? 6 : null, 1);
            matches.add(finalMatch);
            additional.add(finalMatch);
        }
        var input = read(List.of(second, first), rounds, matches, additional);
        var calls = new ArrayList<Long>();
        var query = new RoundRobinRankingQuery(id -> { calls.add(id); return Optional.of(input); });
        var result = query.ranking(42L);
        assertThat(calls).containsExactly(42L);
        assertThat(result.tournamentId()).isEqualTo(42L);
        assertThat(result.tournamentName()).isEqualTo("QA");
        assertThat(result.totalRounds()).isEqualTo(1);
        assertThat(result.completedRounds()).isEqualTo(played ? 1 : 0);
        assertThat(result.totalTeams()).isEqualTo(2);
        assertThat(result.finished()).isEqualTo(played && (!extra || finalPlayed));
        assertThat(result.roundRobinComplete()).isEqualTo(played);
        assertThat(result.scoringMode()).isEqualTo(mode);
        assertThat(result.pointsPerMatch()).isEqualTo(32);
        assertThat(result.courts()).isEqualTo(2);
        assertThat(result.ranking()).extracting(row -> row.team().id()).containsExactly(1L, 2L);
        assertThat(result.ranking()).extracting(row -> row.position()).containsExactly(1,
                mode == ScoringMode.SETS && !played ? 1 : 2);
        assertThat(result.ranking().get(0).team()).isSameAs(first);
        assertThat(result.ranking().get(1).team()).isSameAs(second);
        assertThat(result.finalRanking()).extracting(row -> row.teamId())
                .containsExactly(finalPlayed ? 2L : 1L, finalPlayed ? 1L : 2L);
        assertThat(result.finalRanking()).extracting(row -> row.position()).containsExactly(1,
                mode == ScoringMode.SETS && !played ? 1 : 2);
        assertThat(result.finalRanking().get(0).displayName()).isEqualTo(finalPlayed ? "Team 2" : "Team 1");
        if (extra) {
            assertThat(result.additionalMatches()).containsExactly(new RoundRobinRanking.AdditionalMatch(
                    100L, 1, "Final QA", 1, 1L, 1, "Team 1", 2L, 2, "Team 2",
                    finalPlayed ? 4 : null, finalPlayed ? 6 : null,
                    finalPlayed ? Status.COMPLETED : Status.IN_PROGRESS));
        } else assertThat(result.additionalMatches()).isEmpty();
        assertThat(input.rankingTeams()).containsExactly(second, first);
        assertThat(matches).hasSize(extra ? 2 : 1);
        assertThat(query.compose(input)).isEqualTo(result);
        assertThat(calls).hasSize(1);
    }

    @Test
    void guestNullableLevelAndSidesRemainValuesOfTheOriginalTeam() {
        var player = new Player(10L, "Player QA", "player@example.test", "+5491100000088", null, Side.LEFT);
        var guest = new Team(1L, 42L, 1, "Player QA / Guest QA", TeamStatus.NO_SHOW,
                player, null, "Guest QA", "+5491100000099", statistics(ScoringMode.POINTS, false, true));
        var result = query(read(List.of(guest), List.of(), List.of(), List.of())).ranking(42L);
        assertThat(result.ranking().get(0).team()).isEqualTo(guest);
        assertThat(result.finalRanking()).containsExactly(new RoundRobinRanking.FinalStanding(1L, 1, guest.displayName(), 1));
    }

    @Test
    void pointsDrawKeepsCountersAndSequentialPlaces() {
        var stats = new Statistics(16, 1, 0, 0, 1, 16, 16, 0, 0, 0, 0);
        var result = query(read(List.of(team(2L, 2, stats), team(1L, 1, stats)),
                List.of(new Round(null, Status.COMPLETED, ScoringMode.POINTS, 32, 1)),
                List.of(match(null, Status.COMPLETED, 16, 16, null)), List.of())).ranking(42L);
        assertThat(result.ranking()).extracting(row -> row.position()).containsExactly(1, 2);
        assertThat(result.ranking()).allSatisfy(row -> assertThat(row.team().statistics().drawn()).isEqualTo(1));
    }

    @Test
    void nullModeAndConfigUseFallbacksDuringPartialCircle() {
        var rounds = List.of(new Round(null, Status.IN_PROGRESS, null, null, null),
                new Round(null, Status.COMPLETED, ScoringMode.SETS, 6, 5));
        var result = query(read(List.of(team(1L, 1, statistics(ScoringMode.POINTS, true, true))),
                rounds, List.of(), List.of())).ranking(42L);
        assertThat(result.scoringMode()).isEqualTo(ScoringMode.POINTS);
        assertThat(result.pointsPerMatch()).isEqualTo(32);
        assertThat(result.courts()).isEqualTo(1);
        assertThat(result.totalRounds()).isEqualTo(2);
        assertThat(result.completedRounds()).isEqualTo(1);
        assertThat(result.finished()).isFalse();
        assertThat(result.roundRobinComplete()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ScoringMode.class)
    void unplacedAndOtherPlacementsDoNotChangeCircleStatistics(ScoringMode mode) {
        var teams = List.of(team(1L, 1, statistics(mode, true, true)), team(2L, 2, statistics(mode, true, false)),
                team(3L, 3, statistics(mode, false, false)), team(4L, 4, statistics(mode, false, false)));
        var extra = new Match(101L, 3L, 4L, Phase.PLACEMENT, Status.COMPLETED, 4, 6, 2, 3, "Bronze");
        var free = new Match(102L, 1L, 2L, Phase.PLACEMENT, Status.IN_PROGRESS, null, null, 1, null, "");
        var circle = new Round(null, Status.COMPLETED, mode, 32, 2);
        var before = query(read(teams, List.of(circle), List.of(), List.of())).ranking(42L);
        var result = query(read(teams, List.of(circle, new Round(Phase.PLACEMENT, Status.IN_PROGRESS, mode, 32, 2)),
                List.of(extra, free), List.of(extra, free))).ranking(42L);
        assertThat(result.ranking()).isEqualTo(before.ranking());
        assertThat(result.finalRanking()).extracting(row -> row.teamId()).containsExactly(1L, 2L, 4L, 3L);
        assertThat(result.finished()).isFalse();
        assertThat(result.roundRobinComplete()).isTrue();
        assertThat(result.additionalMatches().get(1).placementStart()).isNull();
    }

    @Test
    void setsUsesOnlyCompletedScoredCircleMeetings() {
        var stats = new Statistics(0, 2, 1, 1, 0, 0, 0, 1, 1, 10, 10);
        var rows = List.of(team(1L, 1, stats), team(2L, 2, stats));
        var result = query(read(rows, List.of(new Round(null, Status.COMPLETED, ScoringMode.SETS, 32, 1)),
                List.of(match(null, Status.COMPLETED, 4, 6, null),
                        match(Phase.QUALIFICATION, Status.COMPLETED, 6, 0, null),
                        match(null, Status.IN_PROGRESS, 6, 0, null), match(null, Status.COMPLETED, null, 6, null)), List.of()))
                .ranking(42L);
        assertThat(result.ranking()).extracting(row -> row.team().id()).containsExactly(2L, 1L);
        assertThat(result.ranking()).extracting(row -> row.position()).containsExactly(1, 2);
    }

    @Test
    void absenceIsAnApplicationErrorWithoutWebStatus() {
        assertThatThrownBy(() -> new RoundRobinRankingQuery(id -> Optional.empty()).ranking(-1377L))
                .isInstanceOf(RoundRobinNotFound.class).hasMessage("Tournament not found: -1377");
    }

    private RoundRobinRankingQuery query(RoundRobinReadSet values) {
        return new RoundRobinRankingQuery(id -> Optional.of(values));
    }

    private RoundRobinReadSet read(List<Team> teams, List<Round> rounds, List<Match> matches, List<Match> extra) {
        return new RoundRobinReadSet(new Event(42L, "QA"), teams, rounds, matches, teams, extra);
    }

    private Team team(Long id, int number, Statistics stats) {
        return new Team(id, 42L, number, "Team " + number, TeamStatus.ACTIVE, null, null, null, null, stats);
    }

    private Match match(Phase phase, Status status, Integer first, Integer second, Integer placement) {
        return new Match(100L, 1L, 2L, phase, status, first, second, 1, placement, "Final QA");
    }

    private Statistics statistics(ScoringMode mode, boolean played, boolean won) {
        if (!played) return new Statistics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        return mode == ScoringMode.SETS
                ? new Statistics(0, 1, won ? 1 : 0, won ? 0 : 1, 0, 0, 0, won ? 1 : 0, won ? 0 : 1, won ? 6 : 4, won ? 4 : 6)
                : new Statistics(won ? 18 : 14, 1, won ? 1 : 0, won ? 0 : 1, 0, won ? 18 : 14, won ? 14 : 18, 0, 0, 0, 0);
    }
}
