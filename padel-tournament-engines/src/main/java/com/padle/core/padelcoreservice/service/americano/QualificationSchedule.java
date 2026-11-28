package com.padle.core.padelcoreservice.service.americano;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** Рекомендации проверяются последовательно на общем остатке, включая неготовые команды. */
final class QualificationSchedule {
    static final long COMPARABLE_WAIT_SECONDS = 300;
    record Team(long id, int number, boolean ready, Boolean firstWon, long waitSeconds) {}
    record Meeting(long first, long second) {}
    record Suggestion(long first, long second, boolean firstMatch, boolean extra, boolean mixed) {}
    record Check(boolean safe, boolean repeat, List<Long> stranded) {}

    static Check check(List<Team> teams, List<Meeting> meetings, Meeting proposed) {
        var state = new State(teams, meetings);
        int first = state.ids.indexOf(proposed.first());
        int second = state.ids.indexOf(proposed.second());
        if (first < 0 || second < 0 || first == second) {
            return new Check(false, false, List.of(proposed.first(), proposed.second()));
        }
        boolean repeat = state.used.contains(QualificationCompletion.Pair.of(proposed.first(), proposed.second()));
        state.add(proposed);
        boolean safe = !repeat && QualificationCompletion.complete(state.ids, state.remaining, state.used).isPresent();
        return new Check(safe, repeat, safe ? List.of() : state.outstanding());
    }

    static List<Suggestion> suggest(List<Team> teams, List<Meeting> meetings) {
        var state = new State(teams, meetings);
        var ready = teams.stream().filter(Team::ready).filter(t -> state.degree(t.id()) > 0)
                .sorted(Comparator.comparingInt(Team::number).thenComparingLong(Team::id)).toList();
        var result = new ArrayList<Suggestion>();
        var assigned = new HashSet<Long>();
        while (true) {
            var choices = new ArrayList<Suggestion>();
            for (int i = 0; i < ready.size(); i++) {
                for (int j = i + 1; j < ready.size(); j++) {
                    var first = ready.get(i);
                    var second = ready.get(j);
                    if (assigned.contains(first.id()) || assigned.contains(second.id())) continue;
                    int firstDegree = state.degree(first.id());
                    int secondDegree = state.degree(second.id());
                    boolean initial = firstDegree == 2 && secondDegree == 2;
                    boolean extra = firstDegree + secondDegree == 3 && state.untouched() == 1
                            && (Boolean.FALSE.equals(first.firstWon()) || Boolean.FALSE.equals(second.firstWon()));
                    boolean mixed = firstDegree == 1 && secondDegree == 1
                            && first.firstWon() != null && second.firstWon() != null
                            && !first.firstWon().equals(second.firstWon());
                    if (!initial && !extra && (firstDegree != 1 || secondDegree != 1)) continue;
                    if (mixed && (state.untouched() > 0 || teams.stream()
                            .anyMatch(t -> state.degree(t.id()) == 1 && t.firstWon() == null))) continue;
                    var meeting = new Meeting(first.id(), second.id());
                    if (!check(teams, state.meetings, meeting).safe()) continue;
                    if (state.untouched() == 0 && teams.stream().noneMatch(t -> state.degree(t.id()) == 1 && t.firstWon() == null)
                            && !state.preservesMixedMinimum(meeting, teams)) continue;
                    choices.add(new Suggestion(first.id(), second.id(), initial, extra, mixed));
                }
            }
            if (choices.isEmpty()) return List.copyOf(result);
            var byId = teams.stream().collect(java.util.stream.Collectors.toMap(Team::id, t -> t));
            long longest = choices.stream().mapToLong(p -> Math.max(byId.get(p.first()).waitSeconds(),
                    byId.get(p.second()).waitSeconds())).max().orElse(0);
            var chosen = choices.stream().min(Comparator
                    .comparingInt((Suggestion p) -> p.firstMatch() ? 0 : p.extra() ? 1 : 2)
                    .thenComparingInt(p -> Math.max(byId.get(p.first()).waitSeconds(),
                            byId.get(p.second()).waitSeconds()) >= longest - COMPARABLE_WAIT_SECONDS ? 0 : 1)
                    .thenComparingInt(p -> p.mixed() ? 1 : 0)
                    .thenComparingInt(p -> -losers(p, byId))
                    .thenComparingLong(p -> -Math.min(byId.get(p.first()).waitSeconds(), byId.get(p.second()).waitSeconds()))
                    .thenComparingLong(p -> -(byId.get(p.first()).waitSeconds() + byId.get(p.second()).waitSeconds()))
                    .thenComparingInt(p -> byId.get(p.first()).number())
                    .thenComparingInt(p -> byId.get(p.second()).number())).orElseThrow();
            result.add(chosen);
            assigned.add(chosen.first());
            assigned.add(chosen.second());
            state.add(new Meeting(chosen.first(), chosen.second()));
        }
    }

    private static int losers(Suggestion pair, Map<Long, Team> teams) {
        return (Boolean.FALSE.equals(teams.get(pair.first()).firstWon()) ? 1 : 0)
                + (Boolean.FALSE.equals(teams.get(pair.second()).firstWon()) ? 1 : 0);
    }

    private static final class State {
        private final List<Long> ids;
        private final int[] remaining;
        private final Set<QualificationCompletion.Pair> used = new HashSet<>();
        private final List<Meeting> meetings;

        State(List<Team> teams, List<Meeting> meetings) {
            ids = teams.stream().map(Team::id).toList();
            remaining = IntStream.range(0, ids.size()).map(i -> 2).toArray();
            this.meetings = new ArrayList<>();
            meetings.forEach(this::add);
        }

        void add(Meeting pair) {
            int first = ids.indexOf(pair.first());
            int second = ids.indexOf(pair.second());
            if (first >= 0) remaining[first]--;
            if (second >= 0) remaining[second]--;
            used.add(QualificationCompletion.Pair.of(pair.first(), pair.second()));
            meetings.add(pair);
        }

        int degree(long id) { return remaining[ids.indexOf(id)]; }
        long untouched() { return java.util.Arrays.stream(remaining).filter(d -> d == 2).count(); }
        List<Long> outstanding() { return IntStream.range(0, ids.size()).filter(i -> remaining[i] > 0).mapToObj(ids::get).toList(); }

        boolean preservesMixedMinimum(Meeting pair, List<Team> teams) {
            var labels = teams.stream().collect(java.util.stream.Collectors.toMap(Team::id,
                    t -> Boolean.TRUE.equals(t.firstWon())));
            int before = minimumMixed(outstanding(), labels, new HashMap<>());
            var rest = outstanding().stream().filter(id -> id != pair.first() && id != pair.second()).toList();
            return (labels.get(pair.first()).equals(labels.get(pair.second())) ? 0 : 1)
                    + minimumMixed(rest, labels, new HashMap<>()) == before;
        }

        private int minimumMixed(List<Long> rest, Map<Long, Boolean> labels, Map<List<Long>, Integer> memo) {
            if (rest.isEmpty()) return 0;
            if (memo.containsKey(rest)) return memo.get(rest);
            long first = rest.get(0);
            int best = 100;
            for (int j = 1; j < rest.size(); j++) {
                long second = rest.get(j);
                if (used.contains(QualificationCompletion.Pair.of(first, second))) continue;
                var tail = rest.stream().filter(id -> id != first && id != second).toList();
                best = Math.min(best, (labels.get(first).equals(labels.get(second)) ? 0 : 1)
                        + minimumMixed(tail, labels, memo));
            }
            memo.put(rest, best);
            return best;
        }
    }
}
