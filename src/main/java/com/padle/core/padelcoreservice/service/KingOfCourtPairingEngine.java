package com.padle.core.padelcoreservice.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Выбор команд на корте «Короля корта» по истории партнёрств турнира. Чистая функция над
 * идентификаторами игроков — не знает о JPA/Spring, легко тестируется без Spring-контекста.
 *
 * <p>Из трёх возможных разбиений четвёрки на две команды выбирается вариант по приоритетам:
 * минимум повторов партнёрства только что завершённого раунда → минимум суммарных совместных
 * матчей за весь турнир → максимум давности повторов → случайный выбор при полном равенстве.</p>
 */
public final class KingOfCourtPairingEngine {

    private KingOfCourtPairingEngine() {
    }

    public record PartnershipKey(Long smallerId, Long largerId) {
        public static PartnershipKey of(Long id1, Long id2) {
            return id1 <= id2 ? new PartnershipKey(id1, id2) : new PartnershipKey(id2, id1);
        }
    }

    public record PartnershipStats(int timesPlayedTogether, int lastRoundNumber) {
    }

    /** Один сыгранный корт: составы двух команд и номер раунда, в котором он был сыгран. */
    public record PlayedCourt(int roundNumber, List<Long> firstTeamPlayerIds, List<Long> secondTeamPlayerIds) {
    }

    public static Map<PartnershipKey, PartnershipStats> buildPartnershipHistory(List<PlayedCourt> playedCourts) {
        Map<PartnershipKey, PartnershipStats> history = new HashMap<>();
        for (PlayedCourt court : playedCourts) {
            recordPartnership(history, court.firstTeamPlayerIds(), court.roundNumber());
            recordPartnership(history, court.secondTeamPlayerIds(), court.roundNumber());
        }
        return history;
    }

    private static void recordPartnership(Map<PartnershipKey, PartnershipStats> history, List<Long> team, int roundNumber) {
        PartnershipKey key = PartnershipKey.of(team.get(0), team.get(1));
        history.merge(key, new PartnershipStats(1, roundNumber),
                (previous, current) -> new PartnershipStats(
                        previous.timesPlayedTogether() + current.timesPlayedTogether(),
                        Math.max(previous.lastRoundNumber(), current.lastRoundNumber())));
    }

    /** Все 3 способа разбить четвёрку игроков на две команды по два человека. */
    public static List<List<List<Long>>> possibleTeamCombinations(List<Long> courtPlayerIds) {
        if (courtPlayerIds.size() != 4) {
            throw new IllegalArgumentException("На корте должно быть ровно 4 игрока, получено: " + courtPlayerIds.size());
        }
        Long a = courtPlayerIds.get(0);
        Long b = courtPlayerIds.get(1);
        Long c = courtPlayerIds.get(2);
        Long d = courtPlayerIds.get(3);
        return List.of(
                List.of(List.of(a, b), List.of(c, d)),
                List.of(List.of(a, c), List.of(b, d)),
                List.of(List.of(a, d), List.of(b, c))
        );
    }

    public static List<List<Long>> selectBestTeams(List<Long> courtPlayerIds,
                                                     Map<PartnershipKey, PartnershipStats> history,
                                                     int justCompletedRoundNumber,
                                                     Random random) {
        List<List<List<Long>>> combinations = possibleTeamCombinations(courtPlayerIds);

        Comparator<List<List<Long>>> byPriority = Comparator
                .comparingInt((List<List<Long>> combo) -> immediateRepeatCount(combo, history, justCompletedRoundNumber))
                .thenComparingInt(combo -> totalHistoryCount(combo, history))
                .thenComparingInt(combo -> recencyScore(combo, history));

        List<List<List<Long>>> ranked = combinations.stream().sorted(byPriority).toList();
        List<List<List<Long>>> best = ranked.stream()
                .filter(combo -> byPriority.compare(combo, ranked.get(0)) == 0)
                .toList();

        return best.get(random.nextInt(best.size()));
    }

    private static int immediateRepeatCount(List<List<Long>> combo, Map<PartnershipKey, PartnershipStats> history,
                                             int justCompletedRoundNumber) {
        return (int) combo.stream()
                .filter(team -> statsFor(team, history)
                        .map(stats -> stats.lastRoundNumber() == justCompletedRoundNumber)
                        .orElse(false))
                .count();
    }

    private static int totalHistoryCount(List<List<Long>> combo, Map<PartnershipKey, PartnershipStats> history) {
        return combo.stream()
                .mapToInt(team -> statsFor(team, history).map(PartnershipStats::timesPlayedTogether).orElse(0))
                .sum();
    }

    private static int recencyScore(List<List<Long>> combo, Map<PartnershipKey, PartnershipStats> history) {
        return combo.stream()
                .mapToInt(team -> statsFor(team, history).map(PartnershipStats::lastRoundNumber).orElse(0))
                .max()
                .orElse(0);
    }

    private static Optional<PartnershipStats> statsFor(List<Long> team, Map<PartnershipKey, PartnershipStats> history) {
        return Optional.ofNullable(history.get(PartnershipKey.of(team.get(0), team.get(1))));
    }
}
