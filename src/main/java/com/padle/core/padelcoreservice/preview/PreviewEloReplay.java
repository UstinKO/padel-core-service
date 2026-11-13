package com.padle.core.padelcoreservice.preview;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class PreviewEloReplay {
    private static final double START = 1500;
    private static final double K = 24;
    private static final double TIE_EPSILON = 1e-9;
    private static final int NO_CATEGORY = 99;
    private final int startStep;

    PreviewEloReplay() {
        this(0);
    }

    PreviewEloReplay(int startStep) {
        this.startStep = startStep;
    }

    record Game(String key, int batch, List<Long> ids, int left, int right, boolean verifiedFinal) {
        Game {
            ids = List.copyOf(ids);
            if (batch < 1 || ids.size() != 4 || Set.copyOf(ids).size() != 4 || left < 0 || right < 0
                    || (verifiedFinal && left == right)) {
                throw new IllegalArgumentException("Некорректный матч Elo: " + key);
            }
        }

        Game withScore(int left, int right) {
            return new Game(key, batch, ids, left, right, verifiedFinal);
        }

        double outcome() {
            return left == right ? 0.5 : left > right ? 1 : 0;
        }
    }

    record Event(String key, LocalDate day, String category, List<Game> games, Set<Long> champions,
                 Set<Long> runnersUp, String title, String format, String resultsUrl) {
        Event {
            games = List.copyOf(games);
            champions = Set.copyOf(champions);
            runnersUp = runnersUp == null ? Set.of() : Set.copyOf(runnersUp);
        }

        Event(String key, LocalDate day, String category, List<Game> games, Set<Long> champions) {
            this(key, day, category, games, champions, Set.of());
        }

        Event(String key, LocalDate day, String category, List<Game> games, Set<Long> champions,
              Set<Long> runnersUp) {
            this(key, day, category, games, champions, runnersUp, null, null, null);
        }

        int level() {
            return switch (category == null ? "" : category) {
                case "C5" -> 5;
                case "C6" -> 6;
                case "C7" -> 7;
                case "C8", "D7" -> 8;
                case "C9", "D8" -> 9;
                case "C10", "D9", "PRINCIPIANTES" -> 10;
                default -> NO_CATEGORY;
            };
        }
    }

    record History(Map<Long, String> names, List<Event> events, Map<Long, Long> aliases) {
        History {
            aliases = aliases == null ? Map.of() : Map.copyOf(aliases);
            var canonicalNames = new HashMap<>(names);
            for (var alias : aliases.entrySet()) {
                if (aliases.containsKey(alias.getValue()) || !names.containsKey(alias.getValue())) {
                    throw new IllegalArgumentException("Некорректная связь профилей Elo: " + alias.getKey());
                }
                canonicalNames.remove(alias.getKey());
            }
            names = Map.copyOf(canonicalNames);
            var links = aliases;
            events = events.stream().map(event -> new Event(event.key(), event.day(), event.category(),
                    event.games().stream().map(game -> new Game(game.key(), game.batch(),
                            game.ids().stream().map(id -> links.getOrDefault(id, id)).toList(),
                            game.left(), game.right(), game.verifiedFinal())).toList(),
                    event.champions().stream().map(id -> links.getOrDefault(id, id)).collect(Collectors.toSet()),
                    event.runnersUp().stream().map(id -> links.getOrDefault(id, id)).collect(Collectors.toSet()),
                    event.title(), event.format(), event.resultsUrl())).toList();
        }

        History(Map<Long, String> names, List<Event> events) {
            this(names, events, Map.of());
        }
    }

    private static final class Record {
        int events;
        int matches;
        int wins;
    }

    private static final class Player {
        final long id;
        final Map<Integer, Record> levels = new HashMap<>();
        final List<PreviewEloRanking.TournamentElo> tournamentHistory = new ArrayList<>();
        double elo;
        int confirmed = NO_CATEGORY;
        int tournaments;
        int matches;
        int titles;
        int secondPlaces;

        Player(long id, double elo) {
            this.id = id;
            this.elo = elo;
        }

        int category() {
            return isConfirmed() ? confirmed : levels.entrySet().stream()
                    .max(Comparator.<Map.Entry<Integer, Record>>comparingInt(entry -> entry.getValue().events)
                            .thenComparingInt(entry -> entry.getValue().matches).thenComparingInt(Map.Entry::getKey))
                    .map(Map.Entry::getKey).orElse(NO_CATEGORY);
        }

        boolean isConfirmed() {
            return confirmed != NO_CATEGORY;
        }

        boolean eligibleFromOneEvent(int level) {
            return level >= confirmed - 1 || !isConfirmed();
        }

        PreviewEloRanking.Achievements achievements() {
            return new PreviewEloRanking.Achievements(tournaments, matches, titles, secondPlaces);
        }
    }

    PreviewEloRanking.Snapshot replay(LocalDate cutoff, Map<Long, String> names, List<Event> input) {
        var players = new HashMap<Long, Player>();
        var events = input.stream().sorted(Comparator.comparing(Event::day)
                .thenComparing(event -> eventSortKey(event.key()))).toList();
        for (var day : events.stream().collect(Collectors.groupingBy(Event::day, LinkedHashMap::new, Collectors.toList())).values()) {
            for (var event : day) {
                double start = event.level() == NO_CATEGORY ? START : START + startStep * (8 - event.level());
                event.games().forEach(game -> game.ids().forEach(id -> players.computeIfAbsent(id,
                        key -> new Player(key, start))));
            }
            for (var event : day) {
                applyEvent(event, players);
            }
            confirmFromMultipleEvents(players.values());
        }
        var rows = ranking(players, names);
        var asOf = events.isEmpty() ? cutoff : events.get(events.size() - 1).day();
        return new PreviewEloRanking.Snapshot(asOf.isBefore(cutoff) ? cutoff : asOf, rows);
    }

    private String eventSortKey(String key) {
        if (key.matches("S-[0-9]+")) {
            return String.format(Locale.ROOT, "S-%020d", Long.parseLong(key.substring(2)));
        }
        return key;
    }

    private void applyEvent(Event event, Map<Long, Player> players) {
        var stats = new HashMap<Long, Record>();
        var before = new HashMap<Long, Double>();
        var finalists = new HashSet<Long>();
        var champions = new HashSet<>(event.champions());
        var runnersUp = new HashSet<>(event.runnersUp());
        var batches = event.games().stream().collect(Collectors.groupingBy(Game::batch, TreeMap::new,
                Collectors.toList()));
        for (var batch : batches.values()) {
            var changes = new HashMap<Player, Double>();
            for (var game : batch) {
                var sides = game.ids().stream().map(players::get).toList();
                sides.forEach(player -> before.putIfAbsent(player.id, player.elo));
                applyGame(game, sides, changes, stats);
                if (game.verifiedFinal()) {
                    finalists.addAll(game.ids());
                    int winningSide = game.left() > game.right() ? 0 : 2;
                    champions.addAll(game.ids().subList(winningSide, winningSide + 2));
                    int losingSide = 2 - winningSide;
                    runnersUp.addAll(game.ids().subList(losingSide, losingSide + 2));
                }
            }
            changes.forEach((player, change) -> player.elo += change);
        }
        stats.forEach((id, evidence) -> {
            var player = players.get(id);
            player.tournaments++;
            player.matches += evidence.matches;
            player.tournamentHistory.add(new PreviewEloRanking.TournamentElo(event.day(), event.category(),
                    player.elo, player.elo - before.get(id), evidence.matches, evidence.wins,
                    event.title(), event.format(), event.resultsUrl()));
            if (champions.contains(id)) {
                player.titles++;
            } else if (runnersUp.contains(id)) {
                player.secondPlaces++;
            }
        });
        if (event.level() != NO_CATEGORY) {
            stats.forEach((id, evidence) -> recordEvent(players.get(id), event.level(), evidence,
                    finalists.contains(id), champions.contains(id)));
        }
    }

    private void applyGame(Game game, List<Player> sides, Map<Player, Double> changes, Map<Long, Record> stats) {
        double gap = sides.get(2).elo + sides.get(3).elo - sides.get(0).elo - sides.get(1).elo;
        double change = K * (game.outcome() - 1 / (1 + Math.pow(10, gap / 800)));
        for (int i = 0; i < sides.size(); i++) {
            var player = sides.get(i);
            changes.merge(player, i < 2 ? change : -change, Double::sum);
            var stat = stats.computeIfAbsent(player.id, ignored -> new Record());
            stat.matches++;
            if (game.outcome() == (i < 2 ? 1 : 0)) {
                stat.wins++;
            }
        }
    }

    private void recordEvent(Player player, int level, Record evidence, boolean finalist, boolean champion) {
        var total = player.levels.computeIfAbsent(level, ignored -> new Record());
        total.events++;
        total.matches += evidence.matches;
        total.wins += evidence.wins;
        if (player.eligibleFromOneEvent(level) && evidence.wins * 2 >= evidence.matches
                && ((champion && evidence.matches >= 5) || (finalist && evidence.matches >= 6))) {
            player.confirmed = Math.min(player.confirmed, level);
        }
    }

    private void confirmFromMultipleEvents(Iterable<Player> players) {
        for (var player : players) {
            player.levels.forEach((level, stats) -> {
                if (stats.events >= 2 && stats.matches >= 6 && stats.wins * 2 >= stats.matches) {
                    player.confirmed = Math.min(player.confirmed, level);
                }
            });
        }
    }

    private List<PreviewEloRanking.Row> ranking(Map<Long, Player> players, Map<Long, String> names) {
        var groups = players.values().stream().collect(Collectors.groupingBy(Player::category, TreeMap::new,
                Collectors.toList()));
        var rows = new ArrayList<PreviewEloRanking.Row>();
        int overallIndex = 0;
        for (var group : groups.entrySet()) {
            var sorted = group.getValue().stream().sorted(Comparator.<Player>comparingDouble(p -> p.elo)
                    .reversed().thenComparingLong(p -> p.id)).toList();
            int level = group.getKey();
            var category = level == NO_CATEGORY ? "—" : "C" + level;
            int categoryPlace = 0;
            int overallPlace = 0;
            Player priorConfirmed = null;
            for (int i = 0; i < sorted.size(); i++) {
                var player = sorted.get(i);
                if (i == 0 || !tied(player, sorted.get(i - 1))) {
                    categoryPlace = i + 1;
                }
                Integer place = null;
                if (player.isConfirmed()) {
                    overallIndex++;
                    if (priorConfirmed == null || !tied(player, priorConfirmed)) {
                        overallPlace = overallIndex;
                    }
                    priorConfirmed = player;
                    place = overallPlace;
                }
                rows.add(new PreviewEloRanking.Row(player.id, names.get(player.id), category, player.isConfirmed(),
                        BigDecimal.valueOf(player.elo), categoryPlace, sorted.size(), place,
                        player.isConfirmed() ? null : progress(player.levels.get(level)),
                        strongerCategories(player, level), player.achievements(), player.tournamentHistory));
            }
        }
        return startStep > 0 ? withOverallEloPlaces(rows) : List.copyOf(rows);
    }

    private List<PreviewEloRanking.Row> withOverallEloPlaces(List<PreviewEloRanking.Row> rows) {
        var sorted = rows.stream().sorted(Comparator.comparing(PreviewEloRanking.Row::elo).reversed()
                .thenComparingLong(PreviewEloRanking.Row::id)).toList();
        var places = new HashMap<Long, Integer>();
        int place = 0;
        for (int i = 0; i < sorted.size(); i++) {
            var row = sorted.get(i);
            if (i == 0 || Math.abs(row.elo().doubleValue() - sorted.get(i - 1).elo().doubleValue()) > TIE_EPSILON) {
                place = i + 1;
            }
            places.put(row.id(), place);
        }
        return rows.stream().map(row -> new PreviewEloRanking.Row(row.id(), row.name(), row.category(),
                row.confirmed(), row.elo(), row.categoryPlace(), row.categorySize(), places.get(row.id()),
                row.progress(), row.strongerCategories(), row.achievements(), row.tournamentHistory())).toList();
    }

    private boolean tied(Player first, Player second) {
        return Math.abs(first.elo - second.elo) <= TIE_EPSILON;
    }

    private List<PreviewEloRanking.CategoryProgress> strongerCategories(Player player, int currentLevel) {
        if (currentLevel == NO_CATEGORY) {
            return List.of();
        }
        return IntStream.iterate(currentLevel - 1, level -> level >= 5, level -> level - 1)
                .filter(level -> level == currentLevel - 1 || player.levels.containsKey(level))
                .mapToObj(level -> new PreviewEloRanking.CategoryProgress("C" + level,
                        progress(player.levels.getOrDefault(level, new Record())), player.eligibleFromOneEvent(level)))
                .toList();
    }

    private PreviewEloRanking.Progress progress(Record stats) {
        if (stats == null) {
            return null;
        }
        int tournaments = Math.max(0, 2 - stats.events);
        int matches = Math.max(Math.max(6 - stats.matches, tournaments), stats.matches - 2 * stats.wins);
        int wins = Math.max(0, (stats.matches + matches + 1) / 2 - stats.wins);
        return new PreviewEloRanking.Progress(stats.events, stats.matches, stats.wins, tournaments, matches, wins);
    }
}
