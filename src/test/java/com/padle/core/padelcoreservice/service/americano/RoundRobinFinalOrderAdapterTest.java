package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.RoundRobinStandingDto;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoundRobinFinalOrderAdapterTest {
    @Test
    void emptyCircleStillProcessesCompletedAssignments() {
        assertThat(RoundRobinRules.finalOrder(List.of(), List.of())).isEmpty();
        assertThat(RoundRobinRules.finalOrder(List.of(), List.of(match(1L, 2L, 6, 4, 1)))).isEmpty();
        assertThatThrownBy(() -> RoundRobinRules.finalOrder(List.of(), List.of(match(1L, 2L, null, 4, 1))))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void unchangedCircleKeepsSharedPlaces() {
        assertOrder(RoundRobinRules.finalOrder(circle(1, 1, 3, 4), List.of()),
                new Long[]{1L, 2L, 3L, 4L}, 1, 1, 3, 4);
    }

    @Test
    void finalAndBronzeMoveRemainingRowsIntoFreeSlots() {
        assertOrder(RoundRobinRules.finalOrder(circle(1, 2, 3, 4, 5, 6), List.of(
                match(5L, 6L, 6, 4, 1), match(3L, 4L, 4, 6, 3))),
                new Long[]{5L, 6L, 4L, 3L, 1L, 2L}, 1, 2, 3, 4, 5, 6);
    }

    @Test
    void otherPlacesPreserveTiesAboveThem() {
        assertOrder(RoundRobinRules.finalOrder(circle(1, 1, 3, 3, 5), List.of(match(5L, 4L, 6, 4, 4))),
                new Long[]{1L, 2L, 3L, 5L, 4L}, 1, 1, 3, 4, 5);
    }

    @Test
    void remainingTieUsesFirstAvailableSlot() {
        assertOrder(RoundRobinRules.finalOrder(circle(1, 1, 1, 4, 5), List.of(match(4L, 5L, 6, 4, 1))),
                new Long[]{4L, 5L, 1L, 2L, 3L}, 1, 2, 3, 3, 3);
    }

    @Test
    void tieAfterAssignedGapMovesTogether() {
        assertOrder(RoundRobinRules.finalOrder(circle(1, 1, 3, 3, 5, 6), List.of(match(6L, 5L, 6, 4, 3))),
                new Long[]{1L, 2L, 6L, 5L, 3L, 4L}, 1, 1, 3, 4, 5, 5);
    }

    @Test
    void pendingAndUnplacedRowsDoNotReadNullScores() {
        var pending = match(null, null, null, null, 1);
        pending.setStatus(AmericanoRoundStatus.IN_PROGRESS);
        var free = match(null, null, null, null, null);
        assertOrder(RoundRobinRules.finalOrder(circle(1, 2, 3, 4), List.of(pending, free)),
                new Long[]{1L, 2L, 3L, 4L}, 1, 2, 3, 4);
    }

    @Test
    void directHelperDoesNotAddPhaseFiltering() {
        var extra = match(3L, 4L, 6, 4, 1);
        extra.setRound(AmericanoRound.builder().phase(TournamentPhase.QUALIFICATION).build());
        assertOrder(RoundRobinRules.finalOrder(circle(1, 2, 3, 4), List.of(extra)),
                new Long[]{3L, 4L, 1L, 2L}, 1, 2, 3, 4);
    }

    @Test
    void internalDrawKeepsExistingSecondSideSelection() {
        assertOrder(RoundRobinRules.finalOrder(circle(1, 2), List.of(match(1L, 2L, 4, 4, 1))),
                new Long[]{2L, 1L}, 1, 2);
    }

    @Test
    void repeatedAssignmentKeepsLastResult() {
        var won = match(3L, 4L, 6, 4, 1);
        var lost = match(3L, 4L, 4, 6, 1);
        assertOrder(RoundRobinRules.finalOrder(circle(1, 2, 3, 4), List.of(won, lost)),
                new Long[]{4L, 3L, 1L, 2L}, 1, 2, 3, 4);
        assertOrder(RoundRobinRules.finalOrder(circle(1, 2, 3, 4), List.of(lost, won)),
                new Long[]{3L, 4L, 1L, 2L}, 1, 2, 3, 4);
    }

    @Test
    void overlappingInternalAssignmentsAreNotNewlyRejected() {
        assertOrder(RoundRobinRules.finalOrder(circle(1, 2, 3, 4), List.of(
                match(1L, 2L, 6, 4, 1), match(3L, 4L, 6, 4, 2))),
                new Long[]{1L, 3L, 4L, 2L}, 1, 2, 3, 4);
    }

    @Test
    void duplicateCircleIdsStillFail() {
        var input = circle(1, 2);
        input.get(1).team().setId(1L);
        assertThatThrownBy(() -> RoundRobinRules.finalOrder(input, List.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownAssignedIdKeepsExistingSlotFailure() {
        assertThatThrownBy(() -> RoundRobinRules.finalOrder(circle(1, 2), List.of(match(99L, 2L, 6, 4, 1))))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void completedPlacedMatchStillRequiresBothScores() {
        assertThatThrownBy(() -> RoundRobinRules.finalOrder(circle(1, 2), List.of(match(1L, 2L, null, 4, 1))))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5})
    void outOfRangeInternalPlacementKeepsExistingFailure(int placement) {
        assertThatThrownBy(() -> RoundRobinRules.finalOrder(circle(1, 2, 3, 4), List.of(match(1L, 2L, 6, 4, placement))))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullEntityStillFails() {
        assertThatThrownBy(() -> RoundRobinRules.finalOrder(List.of(new RoundRobinStandings.Ranked(null, 1)), List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullRankedRowStillFails() {
        assertThatThrownBy(() -> RoundRobinRules.finalOrder(Arrays.asList((RoundRobinStandings.Ranked) null), List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void singletonNullableIdAndNumberAreNotNormalized() {
        var team = team(1L, 1);
        team.setId(null);
        team.setTeamNumber(null);
        var result = RoundRobinRules.finalOrder(List.of(new RoundRobinStandings.Ranked(team, 1)), List.of());
        assertThat(result).containsExactly(new RoundRobinStandingDto(null, null, "? / Guest 1", 1));
    }

    @Test
    void dtoFieldsComeFromOriginalRowsWithoutChangingThem() {
        var first = team(10L, 99);
        var second = team(20L, 1);
        first.setTotalScore(77);
        first.setCurrentPosition(9);
        var input = List.of(new RoundRobinStandings.Ranked(first, 1), new RoundRobinStandings.Ranked(second, 1));
        var result = RoundRobinRules.finalOrder(input, List.of());
        assertThat(result).containsExactly(new RoundRobinStandingDto(10L, 99, "? / Guest 10", 1),
                new RoundRobinStandingDto(20L, 1, "? / Guest 20", 1));
        assertThat(input.get(0).team()).isSameAs(first);
        assertThat(input.get(1).team()).isSameAs(second);
        assertThat(first.getTotalScore()).isEqualTo(77);
        assertThat(first.getCurrentPosition()).isEqualTo(9);
        assertThatThrownBy(() -> result.clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullListsAreNotConvertedToEmpty() {
        assertThatThrownBy(() -> RoundRobinRules.finalOrder(List.of(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RoundRobinRules.finalOrder(null, List.of())).isInstanceOf(NullPointerException.class);
    }

    private List<RoundRobinStandings.Ranked> circle(int... places) {
        return IntStream.range(0, places.length)
                .mapToObj(index -> new RoundRobinStandings.Ranked(team(index + 1L, index + 1), places[index])).toList();
    }

    private AmericanoTeam team(Long id, Integer number) {
        return AmericanoTeam.builder().id(id).teamNumber(number).player2Name("Guest " + id).build();
    }

    private AmericanoMatch match(Long first, Long second, Integer firstScore, Integer secondScore, Integer placement) {
        return AmericanoMatch.builder().team1Id(first).team2Id(second).team1Score(firstScore).team2Score(secondScore)
                .placementStart(placement).status(AmericanoRoundStatus.COMPLETED).build();
    }

    private void assertOrder(List<RoundRobinStandingDto> result, Long[] ids, Integer... places) {
        assertThat(result).extracting(RoundRobinStandingDto::teamId).containsExactly(ids);
        assertThat(result).extracting(RoundRobinStandingDto::position).containsExactly(places);
    }
}
