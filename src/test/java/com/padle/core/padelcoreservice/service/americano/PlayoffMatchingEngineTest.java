package com.padle.core.padelcoreservice.service.americano;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.padle.core.padelcoreservice.service.americano.PlayoffMatchingEngine.Candidate;
import static com.padle.core.padelcoreservice.service.americano.PlayoffMatchingEngine.MatchingResult;
import static com.padle.core.padelcoreservice.service.americano.PlayoffMatchingEngine.Pairing;
import static com.padle.core.padelcoreservice.service.americano.PlayoffMatchingEngine.PriorityOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayoffMatchingEngineTest {

    private final PlayoffMatchingEngine engine = new PlayoffMatchingEngine();

    /**
     * ТЗ §7: "команды с двумя победами получают соперников из числа команд с двумя поражениями;
     * команды с результатом 1–1 играют между собой".
     * Посев = место в квалификации: 2-0 → места 1-2, 1-1 → места 3-6, 0-2 → места 7-8.
     * Точная пара внутри бакета не зафиксирована в примере ТЗ (нет данных о разнице геймов),
     * поэтому проверяем инвариант, а не конкретную комбинацию: ни одна пара не сведена
     * из двух команд одного крайнего бакета.
     */
    @Test
    void quarterFinalStyle_separatesWinnersBucketFromEachOtherAndLosersBucketFromEachOther() {
        List<Candidate> candidates = List.of(
                new Candidate(3L, 1, 0),  // 2-0
                new Candidate(1L, 2, 0),  // 2-0
                new Candidate(6L, 3, 0),  // 1-1
                new Candidate(7L, 4, 0),  // 1-1
                new Candidate(5L, 5, 0),  // 1-1
                new Candidate(4L, 6, 0),  // 1-1
                new Candidate(2L, 7, 0),  // 0-2
                new Candidate(8L, 8, 0)   // 0-2
        );

        MatchingResult result = engine.match(candidates, Map.of());

        assertThat(result.allConstraintsSatisfied()).isTrue();
        assertThat(result.pairings()).hasSize(4);
        assertThat(allTeamIds(result)).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        assertThat(pairedTogether(result, 3L, 1L))
                .as("две команды с 2 победами не должны встретиться друг с другом")
                .isFalse();
        assertThat(pairedTogether(result, 2L, 8L))
                .as("две команды с 2 поражениями не должны встретиться друг с другом")
                .isFalse();
    }

    /**
     * ТЗ §11/§12 (полуфинал): команда 1 и команда 3 обе имеют серию из 3 побед подряд и должны
     * быть разведены по разным полуфиналам. Команда 3 уже играла против команды 6 в квалификации.
     * Ожидаемый результат из примера ТЗ: "Полуфинал 1: Команда 3 — Команда 7;
     * Полуфинал 2: Команда 1 — Команда 6". Полуфинал считается через STRENGTH_FIRST (§12),
     * а не SEED_FIRST (§53), которым формируется четвертьфинал (T8).
     */
    @Test
    void semiFinalStyle_matchesExactExampleFromSpec() {
        List<Candidate> candidates = List.of(
                new Candidate(1L, 1, 3), // 3 победы подряд
                new Candidate(3L, 2, 3), // 3 победы подряд
                new Candidate(6L, 3, 1),
                new Candidate(7L, 4, 1)
        );
        Map<Long, Set<Long>> priorOpponents = Map.of(
                3L, Set.of(5L, 6L),
                6L, Set.of(3L)
        );

        MatchingResult result = engine.match(candidates, priorOpponents, PriorityOrder.STRENGTH_FIRST);

        assertThat(result.allConstraintsSatisfied()).isTrue();
        assertThat(result.pairings()).containsExactlyInAnyOrder(
                new Pairing(1L, 6L),
                new Pairing(3L, 7L));
    }

    /**
     * Демонстрирует, зачем вообще нужен STRENGTH_FIRST: посевное отклонение может быть
     * оптимальным именно у пары, где есть реванш — SEED_FIRST (общий плей-офф, §53) его выберет,
     * пожертвовав анти-реваншем ради посева. STRENGTH_FIRST (полуфинал, §12) поступает наоборот —
     * жертвует посевом, но реванша избегает, раз есть полностью корректная альтернатива.
     */
    @Test
    void strengthFirst_prefersAvoidingRematchOverOptimalSeeding_unlikeSeedFirst() {
        List<Candidate> candidates = List.of(
                new Candidate(1L, 1, 5), // сильнейшая серия побед
                new Candidate(2L, 2, 1),
                new Candidate(3L, 3, 1),
                new Candidate(4L, 4, 5)  // сильнейшая серия побед
        );
        Map<Long, Set<Long>> priorOpponents = Map.of(
                2L, Set.of(4L),
                4L, Set.of(2L)
        );

        MatchingResult seedFirst = engine.match(candidates, priorOpponents, PriorityOrder.SEED_FIRST);
        assertThat(seedFirst.allConstraintsSatisfied())
                .as("SEED_FIRST жертвует анти-реваншем ради оптимального посева")
                .isFalse();
        assertThat(pairedTogether(seedFirst, 2L, 4L)).isTrue();

        MatchingResult strengthFirst = engine.match(candidates, priorOpponents, PriorityOrder.STRENGTH_FIRST);
        assertThat(strengthFirst.allConstraintsSatisfied()).isTrue();
        assertThat(strengthFirst.pairings()).containsExactlyInAnyOrder(
                new Pairing(1L, 2L),
                new Pairing(3L, 4L));
    }

    @Test
    void neverPairsTheTwoStrongestSeedsTogether_whenNoOtherConstraintsApply() {
        // Для 4 кандидатов пары (min,max)+(2й,3й) и (min,3й)+(2й,max) математически
        // всегда дают одинаковую сумму |seed_a - seed_b| — движок вправе выбрать любую
        // из них, но никогда не должен ставить два самых сильных посева друг против друга.
        List<Candidate> candidates = List.of(
                new Candidate(10L, 10, 0),
                new Candidate(20L, 20, 0),
                new Candidate(30L, 30, 0),
                new Candidate(40L, 40, 0)
        );

        MatchingResult result = engine.match(candidates, Map.of());

        assertThat(result.allConstraintsSatisfied()).isTrue();
        assertThat(pairedTogether(result, 10L, 20L))
                .as("два самых сильных посева не должны встретиться друг с другом")
                .isFalse();
    }

    /**
     * ТЗ §12: "Если полностью исключить повторления невозможно, система выбирает вариант
     * с минимальным количеством повторных матчей" — с двумя кандидатами избежать
     * единственной пары-реванша невозможно в принципе.
     */
    @Test
    void returnsWarning_whenRematchIsUnavoidable() {
        List<Candidate> candidates = List.of(
                new Candidate(1L, 1, 0),
                new Candidate(2L, 2, 0)
        );
        Map<Long, Set<Long>> priorOpponents = Map.of(1L, Set.of(2L));

        MatchingResult result = engine.match(candidates, priorOpponents);

        assertThat(result.allConstraintsSatisfied()).isFalse();
        assertThat(result.warning()).isNotBlank();
        assertThat(result.pairings()).containsExactly(new Pairing(1L, 2L));
    }

    @Test
    void emptyCandidateList_returnsEmptyResultWithoutError() {
        MatchingResult result = engine.match(List.of(), Map.of());

        assertThat(result.pairings()).isEmpty();
        assertThat(result.allConstraintsSatisfied()).isTrue();
    }

    @Test
    void oddNumberOfCandidates_throws() {
        List<Candidate> candidates = List.of(
                new Candidate(1L, 1, 0),
                new Candidate(2L, 2, 0),
                new Candidate(3L, 3, 0)
        );

        assertThatThrownBy(() -> engine.match(candidates, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private boolean pairedTogether(MatchingResult result, Long a, Long b) {
        return result.pairings().stream()
                .anyMatch(p -> (p.team1Id().equals(a) && p.team2Id().equals(b))
                        || (p.team1Id().equals(b) && p.team2Id().equals(a)));
    }

    private List<Long> allTeamIds(MatchingResult result) {
        return result.pairings().stream()
                .flatMap(p -> Stream.of(p.team1Id(), p.team2Id()))
                .toList();
    }
}
