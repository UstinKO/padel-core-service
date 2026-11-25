package com.padle.core.application.roundrobin;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.padle.core.application.roundrobin.RoundRobinPositionInput.*;
import static com.padle.core.application.roundrobin.RoundRobinReadSet.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundRobinPositionCommandTest {
    @ParameterizedTest
    @EnumSource(ScoringMode.class)
    void emptyInputIsSavedWithoutAnEventLookup(ScoringMode mode) {
        var port = new MemoryPort(new RoundRobinPositionInput(mode, List.of(), null));
        new RoundRobinPositionCommand(port).update(42L);
        assertThat(port.calls).containsExactly("load:42", "save");
        assertThat(port.saved).isEmpty();
    }

    @Test
    void pointsKeepsSourceIndicesAndDoesNotReadMatchesOrUnusedGames() {
        var low = team(1L, 1, 10, null, null, null, null, null);
        var high = team(2L, 2, 20, null, null, null, null, null);
        var input = List.of(low, high);
        var port = new MemoryPort(new RoundRobinPositionInput(ScoringMode.POINTS, input, null));
        new RoundRobinPositionCommand(port).update(42L);
        assertThat(port.saved).containsExactly(new RoundRobinPosition(1, 2L, 1), new RoundRobinPosition(0, 1L, 2));
        assertThat(input).containsExactly(low, high);
        assertThat(port.calls).containsExactly("load:42", "save");
    }

    @Test
    void pointsSingletonDoesNotNormalizeOrCompareNullableCriteria() {
        var port = new MemoryPort(new RoundRobinPositionInput(ScoringMode.POINTS,
                List.of(team(null, null, null, null, null, null, null, null)), null));
        new RoundRobinPositionCommand(port).update(42L);
        assertThat(port.saved).containsExactly(new RoundRobinPosition(0, null, 1));
    }

    @Test
    void pointsStillRejectsNullWhenTheReachedCriterionNeedsIt() {
        var port = new MemoryPort(new RoundRobinPositionInput(ScoringMode.POINTS, List.of(
                team(1L, 1, 10, 1, null, 0, 0, null), team(2L, 2, 10, 1, 0, 0, 0, null)), null));
        assertThatThrownBy(() -> new RoundRobinPositionCommand(port).update(42L)).isInstanceOf(NullPointerException.class);
        assertThat(port.calls).containsExactly("load:42");
    }

    @Test
    void duplicateIdsAndNumbersKeepTheOriginalRowOrder() {
        var first = team(1L, 1, 10, 1, 12, 2, 0, null);
        var second = team(1L, 1, 10, 1, 12, 2, 0, null);
        var port = new MemoryPort(new RoundRobinPositionInput(ScoringMode.POINTS, List.of(second, first), null));
        new RoundRobinPositionCommand(port).update(42L);
        assertThat(port.saved).containsExactly(new RoundRobinPosition(0, 1L, 1), new RoundRobinPosition(1, 1L, 2));
    }

    @Test
    void setsKeepsSharedPlacesAndOriginalIndices() {
        var port = new MemoryPort(new RoundRobinPositionInput(ScoringMode.SETS, List.of(
                team(3L, 3, 0, 0, 0, 0, 0, 0), team(2L, 2, 0, 1, 0, 0, 0, 2),
                team(1L, 1, 0, 1, 0, 0, 0, 2)), List.of()));
        new RoundRobinPositionCommand(port).update(42L);
        assertThat(port.saved).containsExactly(new RoundRobinPosition(2, 1L, 1), new RoundRobinPosition(1, 2L, 1),
                new RoundRobinPosition(0, 3L, 3));
    }

    @Test
    void setsFiltersPlacementPendingAndMissingScoresBeforeMeetings() {
        var teams = List.of(team(1L, 1, 0, 1, 0, 0, 0, 0), team(2L, 2, 0, 1, 0, 0, 0, 0));
        var meetings = List.of(new PositionMatch(1L, 2L, Phase.PLACEMENT, Status.COMPLETED, 6, 0),
                new PositionMatch(1L, 2L, null, Status.PENDING, 6, 0),
                new PositionMatch(1L, 2L, null, Status.COMPLETED, null, 0),
                new PositionMatch(2L, 1L, null, Status.COMPLETED, 6, 4));
        var port = new MemoryPort(new RoundRobinPositionInput(ScoringMode.SETS, teams, meetings));
        new RoundRobinPositionCommand(port).update(42L);
        assertThat(port.saved).containsExactly(new RoundRobinPosition(1, 2L, 1), new RoundRobinPosition(0, 1L, 2));
        assertThat(meetings).hasSize(4);
    }

    @ParameterizedTest
    @EnumSource(ScoringMode.class)
    void readAndWriteUseTheSameCalculationWithoutChangingReadRows(ScoringMode mode) {
        var low = team(1L, 1, 10, 0, 10, 20, 1, -10);
        var high = team(2L, 2, 20, 1, 20, 10, 0, 10);
        var inputs = List.of(low, high);
        var read = inputs.stream().map(this::readTeam).toList();
        var written = RoundRobinStandingsCalculation.rank(new RoundRobinPositionInput(mode, inputs, List.of()));
        assertThat(RoundRobinStandingsCalculation.rankRead(read, List.of(), mode)).isEqualTo(written);
        assertThat(read).extracting(Team::id).containsExactly(1L, 2L);
    }

    @Test
    void pointsReadDoesNotCalculateNullableSetStatistics() {
        var row = new Team(1L, 42L, 1, "QA", TeamStatus.ACTIVE, null, null, null, null,
                new Statistics(10, 1, 1, 0, 0, 10, 0, null, null, null, null));
        assertThat(RoundRobinStandingsCalculation.rankRead(List.of(row), null, ScoringMode.POINTS))
                .containsExactly(new RoundRobinPosition(0, 1L, 1));
    }

    @Test
    void eachUpdateReloadsTheCurrentInputAndPropagatesSaveFailures() {
        var port = new MemoryPort(new RoundRobinPositionInput(ScoringMode.POINTS, List.of(), List.of()));
        var command = new RoundRobinPositionCommand(port);
        command.update(42L);
        port.input = new RoundRobinPositionInput(ScoringMode.POINTS, List.of(team(2L, 1, 10, 0, 0, 0, 0, null)), null);
        port.failSave = true;
        assertThatThrownBy(() -> command.update(42L)).isInstanceOf(SaveFailure.class);
        assertThat(port.saved).containsExactly(new RoundRobinPosition(0, 2L, 1));
        assertThat(port.calls).containsExactly("load:42", "save", "load:42", "save");
    }

    private PositionTeam team(Long id, Integer number, Integer score, Integer wins, Integer scored,
                              Integer conceded, Integer losses, Integer difference) {
        return new PositionTeam(id, number, score, wins, scored, conceded, losses, difference);
    }

    private Team readTeam(PositionTeam team) {
        return new Team(team.id(), 42L, team.number(), "QA", TeamStatus.ACTIVE, null, null, null, null,
                new Statistics(team.totalScore(), 0, team.matchesWon(), team.matchesLost(), 0,
                        team.pointsScored(), team.pointsConceded(), 0, 0, team.gameDifference(), 0));
    }

    private static final class MemoryPort implements RoundRobinPositionPort {
        private RoundRobinPositionInput input;
        private final List<String> calls = new ArrayList<>();
        private List<RoundRobinPosition> saved = List.of();
        private boolean failSave;
        MemoryPort(RoundRobinPositionInput input) { this.input = input; }
        @Override public RoundRobinPositionInput load(Long id) { calls.add("load:" + id); return input; }
        @Override public void savePositions(List<RoundRobinPosition> positions) {
            calls.add("save");
            saved = positions;
            if (failSave) { throw new SaveFailure(); }
        }
    }

    private static final class SaveFailure extends RuntimeException {}
}
