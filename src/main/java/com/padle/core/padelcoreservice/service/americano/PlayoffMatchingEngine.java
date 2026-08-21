package com.padle.core.padelcoreservice.service.americano;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Универсальный движок подбора соперников для стадий плей-офф (QF/SF/1-8-финал) —
 * не привязан к конкретной стадии, переиспользуется T8 (четвертьфинал) и T9 (полуфинал).
 * <p>
 * Перебирает все варианты разбиения кандидатов на пары ("перебрать комбинации
 * и выбрать лучшую") и выбирает лучший по строгим приоритетам ТЗ. Порядок приоритетов
 * не универсален для всех стадий — ТЗ §53/§11 (плей-офф в целом, используется T8 для QF)
 * ставит посев выше разведения сильнейших, а §12 (специально про полуфинал, T9) — наоборот,
 * разведение сильнейших и анти-реванш важнее посева. См. {@link PriorityOrder}.
 * <p>
 * Приоритеты сравниваются лексикографически — более высокий приоритет никогда
 * не приносится в жертву ради более низкого.
 */
@Component
public class PlayoffMatchingEngine {

    public record Candidate(Long teamId, int seed, int winStreak) {
    }

    public record Pairing(Long team1Id, Long team2Id) {
    }

    public record MatchingResult(List<Pairing> pairings, boolean allConstraintsSatisfied, String warning) {
    }

    public enum PriorityOrder {
        /** ТЗ §53/§11: посев ("сильный слабому") → разведение сильнейших → анти-реванш. Используется для QF (T8). */
        SEED_FIRST,
        /** ТЗ §12: разведение сильнейших → анти-реванш → посев — специфичный порядок для полуфинала (T9). */
        STRENGTH_FIRST,
        /**
         * LFPT-348: анти-реванш → разведение групп по результату квалификации (2-0/1-1/0-2) → посев —
         * порядок приоритетов организатора для посева четвертьфинала/play-in сразу из квалификации
         * (в отличие от {@link #SEED_FIRST}, где посев важнее анти-реванша).
         */
        REMATCH_FIRST
    }

    /** Эквивалентно {@code match(candidates, priorOpponents, PriorityOrder.SEED_FIRST)} — приоритеты общего плей-офф (§53/§11). */
    public MatchingResult match(List<Candidate> candidates, Map<Long, Set<Long>> priorOpponents) {
        return match(candidates, priorOpponents, PriorityOrder.SEED_FIRST);
    }

    /**
     * @param candidates      команды, которые нужно разбить на пары (чётное количество)
     * @param priorOpponents  история встреч: teamId -> множество teamId, с которыми команда уже играла
     * @param priorityOrder   в каком порядке сравнивать критерии — общий плей-офф (QF) или полуфинал
     */
    public MatchingResult match(List<Candidate> candidates, Map<Long, Set<Long>> priorOpponents,
                                 PriorityOrder priorityOrder) {
        if (candidates.size() % 2 != 0) {
            throw new IllegalArgumentException(
                    "Matching requires an even number of candidates, got " + candidates.size());
        }
        if (candidates.isEmpty()) {
            return new MatchingResult(List.of(), true, null);
        }

        Map<Long, Candidate> byId = candidates.stream()
                .collect(Collectors.toMap(Candidate::teamId, c -> c));
        Set<Long> strongGroup = topStreakGroup(candidates);
        int idealSeedDistance = idealSeedDistance(candidates);

        List<List<Pairing>> allMatchings = new ArrayList<>();
        enumerateMatchings(new ArrayList<>(candidates), new ArrayList<>(), allMatchings);

        List<Pairing> best = null;
        int[] bestRawCost = null;
        int[] bestOrderedCost = null;
        for (List<Pairing> matching : allMatchings) {
            int[] rawCost = costOf(matching, byId, strongGroup, idealSeedDistance, priorOpponents);
            int[] orderedCost = applyPriorityOrder(rawCost, priorityOrder);
            if (bestOrderedCost == null || compareCost(orderedCost, bestOrderedCost) < 0) {
                bestOrderedCost = orderedCost;
                bestRawCost = rawCost;
                best = matching;
            }
        }

        boolean allSatisfied = bestRawCost[1] == 0 && bestRawCost[2] == 0;
        return new MatchingResult(best, allSatisfied, allSatisfied ? null : buildWarning(bestRawCost));
    }

    /** Переставляет [посев, разведение, реванш] под нужный порядок приоритетов, не меняя сами значения. */
    private int[] applyPriorityOrder(int[] rawCost, PriorityOrder order) {
        return switch (order) {
            case SEED_FIRST -> rawCost; // [посев, разведение, реванш]
            case STRENGTH_FIRST -> new int[]{rawCost[1], rawCost[2], rawCost[0]}; // [разведение, реванш, посев]
            case REMATCH_FIRST -> new int[]{rawCost[2], rawCost[1], rawCost[0]}; // [реванш, разведение, посев]
        };
    }

    /** Рекурсивно перечисляет все варианты разбиения на пары: фиксируем первого кандидата и перебираем его партнёра. */
    private void enumerateMatchings(List<Candidate> remaining, List<Pairing> current, List<List<Pairing>> results) {
        if (remaining.isEmpty()) {
            results.add(new ArrayList<>(current));
            return;
        }
        Candidate first = remaining.get(0);
        for (int i = 1; i < remaining.size(); i++) {
            Candidate partner = remaining.get(i);
            List<Candidate> next = new ArrayList<>(remaining);
            next.remove(i);
            next.remove(0);

            current.add(new Pairing(first.teamId(), partner.teamId()));
            enumerateMatchings(next, current, results);
            current.remove(current.size() - 1);
        }
    }

    /** [посевное отклонение, нарушений разведения сильнейших, повторных встреч] — сравниваются лексикографически. */
    private int[] costOf(List<Pairing> matching, Map<Long, Candidate> byId, Set<Long> strongGroup,
                          int idealSeedDistance, Map<Long, Set<Long>> priorOpponents) {
        int actualSeedDistance = 0;
        int streakViolations = 0;
        int rematchViolations = 0;

        for (Pairing p : matching) {
            Candidate a = byId.get(p.team1Id());
            Candidate b = byId.get(p.team2Id());
            actualSeedDistance += Math.abs(a.seed() - b.seed());

            if (strongGroup.contains(p.team1Id()) && strongGroup.contains(p.team2Id())) {
                streakViolations++;
            }
            if (priorOpponents.getOrDefault(p.team1Id(), Set.of()).contains(p.team2Id())) {
                rematchViolations++;
            }
        }

        int seedingDeviation = idealSeedDistance - actualSeedDistance; // 0 = максимально возможный разброс посева
        return new int[]{seedingDeviation, streakViolations, rematchViolations};
    }

    private int compareCost(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            int cmp = Integer.compare(a[i], b[i]);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    /**
     * "Сильнейшие" — верхняя половина кандидатов по серии побед (столько же, сколько будет пар),
     * чтобы в идеальном случае в каждой паре оказывалось не больше одного из них.
     */
    private Set<Long> topStreakGroup(List<Candidate> candidates) {
        int groupSize = candidates.size() / 2;
        return candidates.stream()
                .sorted(Comparator.comparingInt(Candidate::winStreak).reversed()
                        .thenComparingInt(Candidate::seed))
                .limit(groupSize)
                .map(Candidate::teamId)
                .collect(Collectors.toSet());
    }

    /**
     * Максимально достижимая сумма |seed_a - seed_b| по всем парам — достигается зеркальным
     * разбиением отсортированных посевов (самый сильный с самым слабым и т.д.), что и является
     * математическим определением "сильный против слабого" для любого набора посевов.
     */
    private int idealSeedDistance(List<Candidate> candidates) {
        List<Integer> seeds = candidates.stream()
                .map(Candidate::seed)
                .sorted()
                .toList();
        int n = seeds.size();
        int total = 0;
        for (int i = 0; i < n / 2; i++) {
            total += seeds.get(n - 1 - i) - seeds.get(i);
        }
        return total;
    }

    private String buildWarning(int[] cost) {
        if (cost[2] > 0) {
            return "Полностью исключить повторные матчи невозможно. Предложен вариант с минимальным количеством повторений.";
        }
        return "Не удалось развести всех сильнейших команд по разным парам. Предложен наиболее сбалансированный вариант.";
    }
}
