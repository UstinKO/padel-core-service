package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.service.americano.PlayoffMatchingEngine.Candidate;
import com.padle.core.padelcoreservice.service.americano.PlayoffMatchingEngine.Pairing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** Рассчитывает 1/8 по всем допустимым счетам ещё не сыгранных встреч. */
final class EarlyPlayoffSeeding {
    static final int WINNING_GAMES = 6;
    private static final int SCENARIO_LIMIT = 4096;
    static final String OPEN_ROUND_NOTE = "EARLY_ROUND_OF_16";
    static final String CLOSED_ROUND_NOTE = "CONFIRMED_ROUND_OF_16";

    record Standing(Long id, int wins, int gamesWon, int gamesLost) {
        Standing withScore(int won, int lost) {
            return new Standing(id, wins + (won > lost ? 1 : 0), gamesWon + won, gamesLost + lost);
        }
    }

    record Fixture(long id, long first, long second) {}

    record Pair(long first, long second) {
        static Pair of(long a, long b) {
            return new Pair(Math.min(a, b), Math.max(a, b));
        }

        static Pair of(Pairing pairing) {
            return of(pairing.team1Id(), pairing.team2Id());
        }
    }

    private static final Comparator<Standing> RANKING = Comparator.comparingInt(Standing::wins).reversed()
            .thenComparing(Comparator.comparingInt((Standing s) -> s.gamesWon() - s.gamesLost()).reversed())
            .thenComparing(Comparator.comparingInt(Standing::gamesWon).reversed())
            .thenComparing(Standing::id);

    private final PlayoffMatchingEngine engine;

    EarlyPlayoffSeeding(PlayoffMatchingEngine engine) {
        this.engine = engine;
    }

    static boolean isEarlyRoundNote(String note) {
        return OPEN_ROUND_NOTE.equals(note) || CLOSED_ROUND_NOTE.equals(note);
    }

    static boolean validScore(int first, int second) {
        return Math.max(first, second) == WINNING_GAMES && Math.min(first, second) >= 0
                && Math.min(first, second) < WINNING_GAMES;
    }

    List<Pairing> roundOf16(List<Standing> standings, Map<Long, Set<Long>> opponents) {
        return engine.seedRoundOf16(candidates(standings), opponents);
    }

    private List<Candidate> candidates(List<Standing> standings) {
        List<Standing> ranked = standings.stream().sorted(RANKING)
                .skip(PlayoffFormat.directToQuarterFinal(standings.size())).toList();
        return IntStream.range(0, ranked.size())
                .mapToObj(i -> new Candidate(ranked.get(i).id(), i + 1, ranked.get(i).wins()))
                .toList();
    }

    record Result(Set<Pair> stable, Set<Pair> possible, boolean exhaustive, boolean limited) {}

    Result evaluate(List<Standing> standings, List<Fixture> remaining, Map<Long, Set<Long>> opponents,
                    Set<Pair> eligible, Pair manual) {
        if (eligible.isEmpty()) {
            return new Result(Set.of(), Set.of(), true, false);
        }
        Map<Long, Integer> position = new HashMap<>();
        for (int i = 0; i < standings.size(); i++) {
            position.put(standings.get(i).id(), i);
        }
        Search search = new Search(opponents, eligible, manual);
        boolean exhaustive = search.visit(new ArrayList<>(standings), position, remaining, 0);
        return new Result(search.limit ? Set.of() : search.stable, search.possible, exhaustive, search.limit);
    }

    private final class Search {
        private final Map<Long, Set<Long>> opponents;
        private final Set<Pair> stable;
        private final Pair manual;
        private final Set<Pair> possible = new HashSet<>();
        private final Map<List<Candidate>, Set<Pair>> cache = new HashMap<>();
        private int scenarios;
        private boolean limit;

        private Search(Map<Long, Set<Long>> opponents, Set<Pair> eligible, Pair manual) {
            this.opponents = opponents;
            this.stable = new LinkedHashSet<>(eligible);
            this.manual = manual;
        }

        private boolean visit(List<Standing> standings, Map<Long, Integer> position, List<Fixture> remaining, int index) {
            if (index == remaining.size()) {
                if (++scenarios > SCENARIO_LIMIT) {
                    limit = true;
                    return false;
                }
                Set<Pair> pairs = cache.computeIfAbsent(candidates(standings), candidates -> {
                    Set<Pair> result = new HashSet<>();
                    engine.seedRoundOf16(candidates, opponents).forEach(pair -> result.add(Pair.of(pair)));
                    return result;
                });
                stable.retainAll(pairs);
                possible.addAll(pairs);
                return !stable.isEmpty() || (manual != null && !possible.contains(manual));
            }

            Fixture fixture = remaining.get(index);
            int first = position.get(fixture.first());
            int second = position.get(fixture.second());
            Standing a = standings.get(first);
            Standing b = standings.get(second);
            try {
                for (int loserGames = 0; loserGames < WINNING_GAMES; loserGames++) {
                    standings.set(first, a.withScore(WINNING_GAMES, loserGames));
                    standings.set(second, b.withScore(loserGames, WINNING_GAMES));
                    if (!visit(standings, position, remaining, index + 1)) return false;
                    standings.set(first, a.withScore(loserGames, WINNING_GAMES));
                    standings.set(second, b.withScore(WINNING_GAMES, loserGames));
                    if (!visit(standings, position, remaining, index + 1)) return false;
                }
                return true;
            } finally {
                standings.set(first, a);
                standings.set(second, b);
            }
        }
    }
}
