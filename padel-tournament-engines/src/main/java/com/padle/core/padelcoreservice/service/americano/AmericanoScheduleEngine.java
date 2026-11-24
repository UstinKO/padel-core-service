package com.padle.core.padelcoreservice.service.americano;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Построение расписания раундов индивидуального Americano. Чистая функция над индексами
 * игроков {@code 0..n-1} — не знает о JPA/Spring, легко тестируется без Spring-контекста.
 *
 * <p>Партнёрские пары строятся методом круга, когда весь турнир играет каждый раунд без bye
 * (это математически гарантирует отсутствие повторов партнёров на полном цикле {@code n-1}
 * раундов — классический факт round-robin 1-factorization). Иначе — минимальным по весу
 * совершенным паросочетанием по накопленной истории партнёрств (лучшее по истории, без
 * гарантии нулевых повторов — для произвольного {@code n} идеал может быть математически
 * недостижим).</p>
 *
 * <p>Группировка партнёрских пар раунда в матчи (кто кому соперник) и назначение кортов —
 * отдельные шаги, оба решаются как точная задача минимального паросочетания/назначения
 * (битмаск-ДП на истории соперников/кортов), а не жадным или случайным перебором.</p>
 */
public final class AmericanoScheduleEngine {

    /** Выше этого размера графа точный битмаск-ДП заменяется жадным эвристическим fallback. */
    private static final int EXACT_LIMIT = 20;

    /** Штраф за третий подряд матч игрока на одном корте (должен доминировать над балансом визитов). */
    private static final int CONSECUTIVE_COURT_PENALTY = 1000;

    /**
     * Сколько полных расписаний построить на кандидатуру, прежде чем выбрать лучшее по
     * {@link #score}. Каждый кандидат — жадный проход по истории с небольшим случайным
     * джиттером при равенстве весов (другой порядок разбора равнозначных вариантов), поэтому
     * несколько попыток расходятся и среди них есть смысл выбирать.
     */
    private static final int CANDIDATE_ATTEMPTS = 100;

    /** Амплитуда джиттера весов для разбора равнозначных вариантов (не меняет порядок реальных весов). */
    private static final int JITTER_SCALE = 8;

    private AmericanoScheduleEngine() {
    }

    public record MatchSlot(int team1A, int team1B, int team2A, int team2B, int court) {
    }

    /**
     * {@code true}, если для {@code n} игроков и {@code courts} кортов каждый раунд может
     * играть весь турнир без bye (значит партнёры методом круга дают 0 повторов), и
     * {@code totalRounds} не превышает длину полного цикла без повторов.
     */
    public static boolean isPerfectCycleAchievable(int n, int courts, int totalRounds) {
        int playersPerRound = courts * 4;
        return n % 4 == 0 && n <= playersPerRound && totalRounds <= n - 1;
    }

    /**
     * Строит {@link #CANDIDATE_ATTEMPTS} кандидатов расписания и возвращает лучший по
     * {@link #score} (минимальный разброс встреч соперников и нарушений по кортам).
     */
    public static List<List<MatchSlot>> generate(int n, int courts, int totalRounds) {
        Random rng = new Random();
        List<List<MatchSlot>> best = null;
        int bestScore = Integer.MAX_VALUE;

        for (int attempt = 0; attempt < CANDIDATE_ATTEMPTS; attempt++) {
            List<List<MatchSlot>> candidate = generateOnce(n, courts, totalRounds, rng);
            int candidateScore = score(candidate, n, courts);
            if (candidateScore < bestScore) {
                bestScore = candidateScore;
                best = candidate;
                if (bestScore == 0) break;
            }
        }
        return best;
    }

    private static List<List<MatchSlot>> generateOnce(int n, int courts, int totalRounds, Random rng) {
        int playersPerRound = courts * 4;
        boolean noByeEver = n % 4 == 0 && n <= playersPerRound;

        int[][] partnerCount = new int[n][n];
        int[][] opponentCount = new int[n][n];
        int[][] courtVisits = new int[n][courts];
        int[] lastCourt = filledWith(n, -1);
        int[] byeCount = new int[n];
        int[] lastByeRound = filledWith(n, -1);
        int[] matchesPlayed = new int[n];

        List<List<MatchSlot>> rounds = new ArrayList<>();

        for (int round = 1; round <= totalRounds; round++) {
            List<Integer> active = noByeEver
                    ? IntStream.range(0, n).boxed().toList()
                    : selectActivePlayers(n, playersPerRound, byeCount, lastByeRound, matchesPlayed, round);

            markByePlayers(n, active, byeCount, lastByeRound, matchesPlayed, round);

            List<int[]> partnerPairs = noByeEver
                    ? circlePairs(n, round - 1)
                    : weightedPartnerPairs(active, partnerCount, rng);

            List<MatchSlot> roundMatches =
                    groupIntoMatches(partnerPairs, opponentCount, courts, courtVisits, lastCourt, rng);
            roundMatches.forEach(m -> {
                registerMatch(partnerCount, opponentCount, matchesPlayed, m);
                registerCourt(courtVisits, lastCourt, m);
            });
            rounds.add(roundMatches);
        }
        return rounds;
    }

    /**
     * Оценка качества расписания для выбора лучшего из {@link #CANDIDATE_ATTEMPTS} кандидатов:
     * чем меньше, тем лучше. Обязательные условия ({@link AmericanoScheduleValidator#validate})
     * не могут быть нарушены построением (круг/точный ДП), поэтому здесь — только
     * оптимизационные критерии (соперники/корты).
     */
    private static int score(List<List<MatchSlot>> rounds, int n, int courts) {
        AmericanoScheduleValidator.ScheduleReport report = AmericanoScheduleValidator.validate(n, courts, rounds);
        int opponentSpread = report.maxOpponentMeetings() - report.minOpponentMeetings();
        int consecutivePenalty = Math.max(0, report.maxConsecutiveSameCourt() - 2);
        return report.hardViolations().size() * 10_000
                + opponentSpread * 100
                + consecutivePenalty * 50
                + report.courtsNeverVisited();
    }

    // ── Выбор активных игроков раунда (bye-ротация) ─────────────────────────

    private static List<Integer> selectActivePlayers(int n, int playersPerRound,
                                                      int[] byeCount, int[] lastByeRound,
                                                      int[] matchesPlayed, int currentRound) {
        int activeTarget = Math.min(playersPerRound, n - (n % 4));
        int benchCount = n - activeTarget;

        List<Integer> benchOrder = IntStream.range(0, n).boxed()
                .sorted(Comparator
                        .comparingInt((Integer i) -> byeCount[i])
                        .thenComparing((Integer i) -> lastByeRound[i] == currentRound - 1)
                        .thenComparingInt(i -> -matchesPlayed[i]))
                .toList();

        Set<Integer> benched = new HashSet<>(benchOrder.subList(0, benchCount));
        return IntStream.range(0, n).filter(i -> !benched.contains(i)).boxed().toList();
    }

    private static void markByePlayers(int n, List<Integer> active, int[] byeCount,
                                       int[] lastByeRound, int[] matchesPlayed, int round) {
        Set<Integer> activeSet = new HashSet<>(active);
        IntStream.range(0, n).filter(i -> !activeSet.contains(i)).forEach(i -> {
            byeCount[i]++;
            lastByeRound[i] = round;
            matchesPlayed[i]++;
        });
    }

    // ── Партнёрские пары ─────────────────────────────────────────────────────

    /**
     * Метод круга: игрок {@code n-1} фиксирован, игроки {@code 0..n-2} вращаются.
     * Раунд {@code r} даёт совершенное паросочетание {@code K_n} — каждая пара ровно
     * в одном раунде из {@code n-1} возможных.
     */
    private static List<int[]> circlePairs(int n, int r) {
        List<int[]> pairs = new ArrayList<>();
        int mod = n - 1;
        pairs.add(new int[]{r % mod, n - 1});
        for (int k = 1; k <= (n - 2) / 2; k++) {
            int a = ((r - k) % mod + mod) % mod;
            int b = (r + k) % mod;
            pairs.add(new int[]{a, b});
        }
        return pairs;
    }

    private static List<int[]> weightedPartnerPairs(List<Integer> active, int[][] partnerCount, Random rng) {
        int m = active.size();
        int[][] weight = new int[m][m];
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                int w = partnerCount[active.get(i)][active.get(j)] * JITTER_SCALE + rng.nextInt(JITTER_SCALE);
                weight[i][j] = w;
                weight[j][i] = w;
            }
        }
        int[] localPartner = minWeightPerfectMatching(weight);
        List<int[]> pairs = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            int j = localPartner[i];
            if (i < j) pairs.add(new int[]{active.get(i), active.get(j)});
        }
        return pairs;
    }

    // ── Группировка партнёрских пар в матчи + назначение кортов ─────────────

    private static List<MatchSlot> groupIntoMatches(List<int[]> partnerPairs, int[][] opponentCount,
                                                     int courts, int[][] courtVisits, int[] lastCourt, Random rng) {
        int pairCount = partnerPairs.size();
        int[][] weight = new int[pairCount][pairCount];
        for (int i = 0; i < pairCount; i++) {
            int[] pi = partnerPairs.get(i);
            for (int j = i + 1; j < pairCount; j++) {
                int[] pj = partnerPairs.get(j);
                int w = opponentCount[pi[0]][pj[0]] + opponentCount[pi[0]][pj[1]]
                        + opponentCount[pi[1]][pj[0]] + opponentCount[pi[1]][pj[1]];
                int jittered = w * JITTER_SCALE + rng.nextInt(JITTER_SCALE);
                weight[i][j] = jittered;
                weight[j][i] = jittered;
            }
        }
        int[] pairPartner = minWeightPerfectMatching(weight);

        List<int[]> matchesNoCourt = new ArrayList<>();
        for (int i = 0; i < pairCount; i++) {
            int j = pairPartner[i];
            if (i < j) {
                int[] a = partnerPairs.get(i);
                int[] b = partnerPairs.get(j);
                matchesNoCourt.add(new int[]{a[0], a[1], b[0], b[1]});
            }
        }

        int[] courtAssignment = assignCourts(matchesNoCourt, courts, courtVisits, lastCourt, rng);

        List<MatchSlot> result = new ArrayList<>();
        for (int i = 0; i < matchesNoCourt.size(); i++) {
            int[] m = matchesNoCourt.get(i);
            result.add(new MatchSlot(m[0], m[1], m[2], m[3], courtAssignment[i] + 1));
        }
        return result;
    }

    private static int[] assignCourts(List<int[]> matches, int courts, int[][] courtVisits, int[] lastCourt,
                                      Random rng) {
        int rows = matches.size();
        int[][] cost = new int[rows][courts];
        for (int m = 0; m < rows; m++) {
            int[] players = matches.get(m);
            for (int c = 0; c < courts; c++) {
                int courtCost = 0;
                for (int p : players) {
                    courtCost += courtVisits[p][c];
                    if (lastCourt[p] == c) {
                        courtCost += CONSECUTIVE_COURT_PENALTY;
                    }
                }
                cost[m][c] = courtCost * JITTER_SCALE + rng.nextInt(JITTER_SCALE);
            }
        }
        return minCostAssignment(cost);
    }

    private static void registerMatch(int[][] partnerCount, int[][] opponentCount, int[] matchesPlayed, MatchSlot m) {
        partnerCount[m.team1A()][m.team1B()]++;
        partnerCount[m.team1B()][m.team1A()]++;
        partnerCount[m.team2A()][m.team2B()]++;
        partnerCount[m.team2B()][m.team2A()]++;
        for (int a : new int[]{m.team1A(), m.team1B()}) {
            for (int b : new int[]{m.team2A(), m.team2B()}) {
                opponentCount[a][b]++;
                opponentCount[b][a]++;
            }
        }
        matchesPlayed[m.team1A()]++;
        matchesPlayed[m.team1B()]++;
        matchesPlayed[m.team2A()]++;
        matchesPlayed[m.team2B()]++;
    }

    private static void registerCourt(int[][] courtVisits, int[] lastCourt, MatchSlot m) {
        int courtIdx = m.court() - 1;
        for (int p : new int[]{m.team1A(), m.team1B(), m.team2A(), m.team2B()}) {
            courtVisits[p][courtIdx]++;
            lastCourt[p] = courtIdx;
        }
    }

    // ── Точные алгоритмы на малых графах (битмаск-ДП) ────────────────────────

    /**
     * Минимальное по сумме весов совершенное паросочетание на {@code n} вершинах ({@code n}
     * чётно). Точный битмаск-ДП при {@code n <= EXACT_LIMIT}, иначе жадный fallback без
     * гарантии оптимума, но всегда завершающийся (никаких зависаний на больших конфигурациях).
     */
    static int[] minWeightPerfectMatching(int[][] weight) {
        int n = weight.length;
        if (n == 0) return new int[0];
        if (n > EXACT_LIMIT) return greedyPerfectMatching(weight);

        int full = 1 << n;
        int[] dp = new int[full];
        int[] prevMask = new int[full];
        int[] pairedWith = new int[full];
        Arrays.fill(dp, Integer.MAX_VALUE);
        dp[0] = 0;

        for (int mask = 0; mask < full - 1; mask++) {
            if (dp[mask] == Integer.MAX_VALUE) continue;
            int first = Integer.numberOfTrailingZeros(~mask);
            for (int j = first + 1; j < n; j++) {
                if ((mask & (1 << j)) != 0) continue;
                int next = mask | (1 << first) | (1 << j);
                int candidate = dp[mask] + weight[first][j];
                if (candidate < dp[next]) {
                    dp[next] = candidate;
                    prevMask[next] = mask;
                    pairedWith[next] = j;
                }
            }
        }

        int[] partner = new int[n];
        int mask = full - 1;
        while (mask != 0) {
            int prev = prevMask[mask];
            int first = Integer.numberOfTrailingZeros(~prev);
            int j = pairedWith[mask];
            partner[first] = j;
            partner[j] = first;
            mask = prev;
        }
        return partner;
    }

    private static int[] greedyPerfectMatching(int[][] weight) {
        int n = weight.length;
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                edges.add(new int[]{i, j, weight[i][j]});
            }
        }
        edges.sort(Comparator.comparingInt(e -> e[2]));

        int[] partner = filledWith(n, -1);
        for (int[] edge : edges) {
            if (partner[edge[0]] == -1 && partner[edge[1]] == -1) {
                partner[edge[0]] = edge[1];
                partner[edge[1]] = edge[0];
            }
        }

        List<Integer> leftover = IntStream.range(0, n).filter(i -> partner[i] == -1).boxed().collect(Collectors.toList());
        for (int i = 0; i + 1 < leftover.size(); i += 2) {
            partner[leftover.get(i)] = leftover.get(i + 1);
            partner[leftover.get(i + 1)] = leftover.get(i);
        }
        return partner;
    }

    /**
     * Минимальное по стоимости назначение {@code rows} задач на {@code rows} из {@code cols}
     * исполнителей ({@code rows <= cols}). Точный битмаск-ДП при {@code cols <= EXACT_LIMIT},
     * иначе жадный fallback.
     */
    static int[] minCostAssignment(int[][] cost) {
        int rows = cost.length;
        if (rows == 0) return new int[0];
        int cols = cost[0].length;
        if (cols > EXACT_LIMIT) return greedyAssignment(cost);

        int full = 1 << cols;
        int[] dp = new int[full];
        int[] choice = new int[full];
        Arrays.fill(dp, Integer.MAX_VALUE);
        dp[0] = 0;

        for (int mask = 0; mask < full; mask++) {
            if (dp[mask] == Integer.MAX_VALUE) continue;
            int row = Integer.bitCount(mask);
            if (row >= rows) continue;
            for (int c = 0; c < cols; c++) {
                if ((mask & (1 << c)) != 0) continue;
                int next = mask | (1 << c);
                int candidate = dp[mask] + cost[row][c];
                if (candidate < dp[next]) {
                    dp[next] = candidate;
                    choice[next] = c;
                }
            }
        }

        int bestMask = -1;
        int bestCost = Integer.MAX_VALUE;
        for (int mask = 0; mask < full; mask++) {
            if (Integer.bitCount(mask) == rows && dp[mask] < bestCost) {
                bestCost = dp[mask];
                bestMask = mask;
            }
        }

        int[] assignment = new int[rows];
        int mask = bestMask;
        for (int row = rows - 1; row >= 0; row--) {
            int c = choice[mask];
            assignment[row] = c;
            mask ^= (1 << c);
        }
        return assignment;
    }

    private static int[] greedyAssignment(int[][] cost) {
        int rows = cost.length;
        int cols = cost[0].length;
        List<int[]> entries = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                entries.add(new int[]{r, c, cost[r][c]});
            }
        }
        entries.sort(Comparator.comparingInt(e -> e[2]));

        int[] assignment = filledWith(rows, -1);
        boolean[] colUsed = new boolean[cols];
        int assignedCount = 0;
        for (int[] entry : entries) {
            if (assignedCount == rows) break;
            int r = entry[0];
            int c = entry[1];
            if (assignment[r] == -1 && !colUsed[c]) {
                assignment[r] = c;
                colUsed[c] = true;
                assignedCount++;
            }
        }
        return assignment;
    }

    private static int[] filledWith(int size, int value) {
        int[] arr = new int[size];
        Arrays.fill(arr, value);
        return arr;
    }
}
