package com.padle.core.padelcoreservice.service.americano;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.padle.core.padelcoreservice.service.americano.QualificationPairing.Pairing;
import static com.padle.core.padelcoreservice.service.americano.QualificationPairing.pairSecondRound;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Итоговое ТЗ §2 (T15): на 10 и 14 командах 2-й тур квалификации даёт нечётное число победителей
 * и нечётное число проигравших после 1-го тура (5 и 5, 7 и 7) — по одному "хвосту" с каждой
 * стороны, которых нельзя сводить друг с другом напрямую.
 */
class QualificationPairingTest {

    private static Set<Long> teamsIn(List<Pairing> pairs) {
        return Stream.concat(pairs.stream().map(Pairing::team1Id), pairs.stream().map(Pairing::team2Id))
                .collect(Collectors.toSet());
    }

    @Test
    void tenTeams_fiveWinnersFiveLosers_tailsDoNotFaceEachOther() {
        List<Long> winners = List.of(1L, 2L, 3L, 4L, 5L);
        List<Long> losers = List.of(6L, 7L, 8L, 9L, 10L);

        List<Pairing> pairs = pairSecondRound(winners, losers);

        assertThat(pairs).hasSize(5);
        assertThat(teamsIn(pairs)).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
        // "Хвостовые" команды — последние в каждом списке (5-й победитель, 10-й проигравший) —
        // не должны оказаться в одной паре друг с другом.
        assertThat(pairs).doesNotContain(new Pairing(5L, 10L)).doesNotContain(new Pairing(10L, 5L));

        long crossMatches = pairs.stream()
                .filter(p -> winners.contains(p.team1Id()) != winners.contains(p.team2Id()))
                .count();
        assertThat(crossMatches).isEqualTo(1); // ровно один "хвостовой" матч нарушает правило — не больше и не меньше
    }

    @Test
    void fourteenTeams_sevenWinnersSevenLosers_tailsDoNotFaceEachOther() {
        List<Long> winners = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L);
        List<Long> losers = List.of(8L, 9L, 10L, 11L, 12L, 13L, 14L);

        List<Pairing> pairs = pairSecondRound(winners, losers);

        assertThat(pairs).hasSize(7);
        assertThat(teamsIn(pairs)).hasSize(14);
        assertThat(pairs).doesNotContain(new Pairing(7L, 14L)).doesNotContain(new Pairing(14L, 7L));

        long crossMatches = pairs.stream()
                .filter(p -> winners.contains(p.team1Id()) != winners.contains(p.team2Id()))
                .count();
        assertThat(crossMatches).isEqualTo(1);
    }

    @Test
    void evenBuckets_noCrossMatchNeeded() {
        // 8 команд: по 4 победителя и 4 проигравших — делится ровно, без исключений (ТЗ §2 их не касается).
        List<Long> winners = List.of(1L, 2L, 3L, 4L);
        List<Long> losers = List.of(5L, 6L, 7L, 8L);

        List<Pairing> pairs = pairSecondRound(winners, losers);

        assertThat(pairs).hasSize(4);
        boolean anyCross = pairs.stream()
                .anyMatch(p -> winners.contains(p.team1Id()) != winners.contains(p.team2Id()));
        assertThat(anyCross).isFalse();
    }

    @Test
    void onlyOneTeamOnEachSide_mustPairDirectly() {
        List<Long> winners = List.of(1L);
        List<Long> losers = List.of(2L);

        List<Pairing> pairs = pairSecondRound(winners, losers);

        assertThat(pairs).containsExactly(new Pairing(1L, 2L));
    }

    @Test
    void oddTailOnOneSideOnly_leavesItUnpaired() {
        List<Long> winners = List.of(1L, 2L, 3L);
        List<Long> losers = List.of();

        List<Pairing> pairs = pairSecondRound(winners, losers);

        assertThat(pairs).hasSize(1);
        assertThat(teamsIn(pairs)).containsExactlyInAnyOrder(1L, 2L);
    }
}
