package com.padle.core.padelcoreservice.service.americano;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ТЗ §36/§41 (T16): команда, ожидавшая победителя 1/8 финала в четвертьфинале, не должна
 * получить в соперники команду, с которой уже играла в квалификации, если этого можно избежать
 * обменом с другой уже укомплектованной парой четвертьфинала (вариант 2).
 */
class PlayoffRematchSwapTest {

    @Test
    void noSwapNeeded_whenNoRematch() {
        Map<Long, Set<Long>> priorOpponents = Map.of();
        List<long[]> siblings = List.of(new long[]{2L, 7L}, new long[]{3L, 6L});

        Optional<Long> swap = PlayoffRematchSwap.findSwap(1L, 8L, siblings, priorOpponents);

        assertThat(swap).isEmpty();
    }

    @Test
    void swapsWithSiblingPair_whenBothResultingPairsAreClean() {
        // 1-е место (waiting=1) уже играло с победителем 1/8 (winner=8) в квалификации.
        // Пара (2,7): если 1 сыграет с 7 — не рематч; если 2 сыграет с 8 — тоже не рематч.
        Map<Long, Set<Long>> priorOpponents = Map.of(1L, Set.of(8L), 8L, Set.of(1L));
        List<long[]> siblings = List.of(new long[]{2L, 7L}, new long[]{3L, 6L});

        Optional<Long> swap = PlayoffRematchSwap.findSwap(1L, 8L, siblings, priorOpponents);

        assertThat(swap).contains(2L); // команда 2 меняется местами с "ожидающей" командой 1
    }

    @Test
    void triesBothSlotsOfSiblingPair_beforeMovingOn() {
        // В паре (2,7): candidate=2 не годится (уже играл с победителем 8),
        // но candidate=7 годится — алгоритм должен проверить оба варианта пары, а не только первый.
        Map<Long, Set<Long>> priorOpponents = Map.of(
                1L, Set.of(8L),
                2L, Set.of(8L)
        );
        List<long[]> siblings = List.of(new long[]{2L, 7L});

        Optional<Long> swap = PlayoffRematchSwap.findSwap(1L, 8L, siblings, priorOpponents);

        assertThat(swap).contains(7L);
    }

    @Test
    void returnsEmpty_whenNoValidSwapExistsAnywhere() {
        // 1 уже играло и с 8 (winner), и со всеми потенциальными партнёрами по обмену.
        Map<Long, Set<Long>> priorOpponents = Map.of(1L, Set.of(8L, 2L, 3L, 6L, 7L));
        List<long[]> siblings = List.of(new long[]{2L, 7L}, new long[]{3L, 6L});

        Optional<Long> swap = PlayoffRematchSwap.findSwap(1L, 8L, siblings, priorOpponents);

        assertThat(swap).isEmpty();
    }

    @Test
    void returnsEmpty_whenNoSiblingPairsAvailable() {
        Map<Long, Set<Long>> priorOpponents = Map.of(1L, Set.of(8L));

        Optional<Long> swap = PlayoffRematchSwap.findSwap(1L, 8L, List.of(), priorOpponents);

        assertThat(swap).isEmpty();
    }
}
