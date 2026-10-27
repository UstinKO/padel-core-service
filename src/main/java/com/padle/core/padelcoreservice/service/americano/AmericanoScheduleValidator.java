package com.padle.core.padelcoreservice.service.americano;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Автоматическая проверка расписания, сгенерированного {@link AmericanoScheduleEngine}.
 * Чистая функция над индексами игроков {@code 0..n-1}.
 */
public final class AmericanoScheduleValidator {

    private AmericanoScheduleValidator() {
    }

    /**
     * @param partnerRepeats          количество партнёрских пар, встретившихся больше одного раза
     * @param playersWithoutPartner   количество пар игроков, не сыгравших вместе ни разу
     * @param minOpponentMeetings     минимальное число встреч как соперники среди всех пар игроков
     * @param maxOpponentMeetings     максимальное число встреч как соперники среди всех пар игроков
     * @param maxConsecutiveSameCourt наибольшая серия подряд идущих матчей одного игрока на одном корте
     * @param courtsNeverVisited      количество (игрок, корт), на котором игрок не играл ни разу
     * @param hardViolations          нарушения обязательных условий (раунд/матч некорректен) — непустой список
     *                                означает, что расписание не должно подтверждаться
     */
    public record ScheduleReport(
            int partnerRepeats,
            int playersWithoutPartner,
            int minOpponentMeetings,
            int maxOpponentMeetings,
            int maxConsecutiveSameCourt,
            int courtsNeverVisited,
            List<String> hardViolations
    ) {
        public boolean isValid() {
            return hardViolations.isEmpty();
        }
    }

    public static ScheduleReport validate(int n, int courts, List<List<AmericanoScheduleEngine.MatchSlot>> rounds) {
        List<String> violations = new ArrayList<>();
        int[][] partnerCount = new int[n][n];
        int[][] opponentCount = new int[n][n];
        int[][] courtVisits = new int[n][courts];
        int[] lastCourt = new int[n];
        int[] consecutiveSameCourt = new int[n];
        Arrays.fill(lastCourt, -1);
        int maxConsecutive = 0;

        for (int roundIdx = 0; roundIdx < rounds.size(); roundIdx++) {
            int roundNumber = roundIdx + 1;
            Set<Integer> playedThisRound = new HashSet<>();

            for (AmericanoScheduleEngine.MatchSlot match : rounds.get(roundIdx)) {
                int[] players = {match.team1A(), match.team1B(), match.team2A(), match.team2B()};

                if (Arrays.stream(players).distinct().count() != 4) {
                    violations.add("Раунд %d: матч содержит не 4 уникальных игрока".formatted(roundNumber));
                }
                for (int p : players) {
                    if (!playedThisRound.add(p)) {
                        violations.add("Раунд %d: игрок %d участвует в раунде больше одного раза".formatted(roundNumber, p));
                    }
                }
                if (match.court() < 1 || match.court() > courts) {
                    violations.add("Раунд %d: матч назначен на некорректный корт %d".formatted(roundNumber, match.court()));
                }

                partnerCount[match.team1A()][match.team1B()]++;
                partnerCount[match.team1B()][match.team1A()]++;
                partnerCount[match.team2A()][match.team2B()]++;
                partnerCount[match.team2B()][match.team2A()]++;

                for (int a : new int[]{match.team1A(), match.team1B()}) {
                    for (int b : new int[]{match.team2A(), match.team2B()}) {
                        opponentCount[a][b]++;
                        opponentCount[b][a]++;
                    }
                }

                int courtIdx = match.court() - 1;
                if (courtIdx >= 0 && courtIdx < courts) {
                    for (int p : players) {
                        courtVisits[p][courtIdx]++;
                        consecutiveSameCourt[p] = lastCourt[p] == courtIdx ? consecutiveSameCourt[p] + 1 : 1;
                        lastCourt[p] = courtIdx;
                        maxConsecutive = Math.max(maxConsecutive, consecutiveSameCourt[p]);
                    }
                }
            }
        }

        int partnerRepeats = 0;
        int playersWithoutPartner = 0;
        int minOpponent = Integer.MAX_VALUE;
        int maxOpponent = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (partnerCount[i][j] > 1) partnerRepeats++;
                if (partnerCount[i][j] == 0) playersWithoutPartner++;
                minOpponent = Math.min(minOpponent, opponentCount[i][j]);
                maxOpponent = Math.max(maxOpponent, opponentCount[i][j]);
            }
        }
        if (n < 2) {
            minOpponent = 0;
            maxOpponent = 0;
        }

        int courtsNeverVisited = 0;
        for (int p = 0; p < n; p++) {
            for (int c = 0; c < courts; c++) {
                if (courtVisits[p][c] == 0) courtsNeverVisited++;
            }
        }

        return new ScheduleReport(partnerRepeats, playersWithoutPartner, minOpponent, maxOpponent,
                maxConsecutive, courtsNeverVisited, violations);
    }
}
