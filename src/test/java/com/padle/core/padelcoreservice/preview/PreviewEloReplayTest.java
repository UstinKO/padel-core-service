package com.padle.core.padelcoreservice.preview;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PreviewEloReplayTest {
    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 10, 17);
    private static final Map<Long, String> NAMES = Map.of(1L, "One", 2L, "Two", 3L, "Three", 4L, "Four",
            5L, "Five", 6L, "Six", 7L, "Seven", 8L, "Eight");
    private final PreviewEloReplay replay = new PreviewEloReplay();

    @Test
    void replaysLaterOpponentsAndReopensCategoryWhenACorrectedFinalNoLongerConfirmsIt() {
        var games = new ArrayList<PreviewEloReplay.Game>();
        for (int batch = 1; batch <= 5; batch++) {
            games.add(game("historic-" + batch, batch, List.of(1L, 2L, 3L, 4L), 6, 4, batch == 5));
        }
        var later = event("S-2", 1, "C6", List.of(game("later", 1, List.of(1L, 5L, 6L, 7L), 11, 10, false)));
        var original = replay.replay(FIRST_DAY, NAMES, List.of(event("S-1", 0, "C5", games), later));
        assertThat(row(original, 1).category()).isEqualTo("C5");
        assertThat(row(original, 1).confirmed()).isTrue();
        assertThat(row(original, 1).overallPlace()).isNotNull();

        games.set(4, games.get(4).withScore(4, 6));
        var corrected = replay.replay(FIRST_DAY, NAMES, List.of(event("S-1", 0, "C5", games), later));
        assertThat(row(corrected, 1).confirmed()).isFalse();
        assertThat(row(corrected, 1).overallPlace()).isNull();
        assertThat(row(corrected, 1).category()).isEqualTo("C5");
        assertThat(row(corrected, 1).elo()).isLessThan(row(original, 1).elo());
        assertThat(row(corrected, 5).elo()).isGreaterThan(row(original, 5).elo());
        assertThat(row(corrected, 1).progress()).isEqualTo(new PreviewEloRanking.Progress(1, 5, 4, 1, 1, 0));
    }

    @Test
    void usesTheSameStartingEloForGamesInOneRoundAndKeepsAPreviousConfirmationAfterLosses() {
        var ids = List.of(1L, 2L, 3L, 4L);
        var first = event("S-9", 0, "C8", List.of(
                game("a", 1, ids, 11, 10, false), game("b", 1, ids, 11, 10, false),
                game("c", 1, ids, 11, 10, false), game("d", 1, ids, 11, 10, false)));
        var initial = replay.replay(FIRST_DAY, NAMES, List.of(first));
        assertThat(row(initial, 1).elo().doubleValue()).isCloseTo(1548, within(1e-9));
        assertThat(row(initial, 1).confirmed()).isFalse();
        var second = event("S-10", 1, "C8", List.of(
                game("e", 1, ids, 10, 11, false), game("f", 2, ids, 10, 11, false)));
        var third = event("S-11", 2, "C8", List.of(
                game("g", 1, ids, 10, 11, false), game("h", 2, ids, 10, 11, false),
                game("i", 3, ids, 10, 11, false), game("j", 4, ids, 10, 11, false)));
        var confirmed = replay.replay(FIRST_DAY, NAMES, List.of(third, second, first));
        assertThat(row(confirmed, 1).confirmed()).isTrue();
        assertThat(row(confirmed, 1).categoryPlace()).isGreaterThan(1);
        assertThat(row(confirmed, 1).overallPlace()).isNotNull();
    }

    @Test
    void requiresTwoTargetEventsForALargeJumpEvenAfterWinningOrPlayingSixMatchesInAFinal() {
        var initial = event("S-1", 0, "C8", winningRun("base", 5));
        var opponents = event("S-2", 0, "C8", winningRun("opponents", 5).stream()
                .map(game -> game.withScore(4, 6)).toList());
        var distant = event("S-3", 1, "C5", winningRun("distant", 6).stream()
                .map(game -> game.batch() <= 3 ? game.withScore(4, 6) : game).toList());
        var afterFirst = replay.replay(FIRST_DAY, NAMES, List.of(initial, opponents, distant));
        assertThat(row(afterFirst, 1).category()).isEqualTo("C8");
        assertThat(row(afterFirst, 3).category()).isEqualTo("C8");
        var progress = new PreviewEloRanking.CategoryProgress("C5",
                new PreviewEloRanking.Progress(1, 6, 3, 1, 1, 1), false);
        assertThat(row(afterFirst, 1).strongerCategories()).containsExactly(progress);
        var seeded = new PreviewEloReplay(40).replay(FIRST_DAY, NAMES, List.of(initial, opponents, distant));
        assertThat(row(seeded, 1).strongerCategories()).containsExactly(progress);
        assertThat(row(afterFirst, 1).elo()).isNotEqualTo(row(
                replay.replay(FIRST_DAY, NAMES, List.of(initial, opponents)), 1).elo());

        var adjacent = event("S-4", 2, "C7", winningRun("adjacent", 5));
        var afterAdjacent = replay.replay(FIRST_DAY, NAMES, List.of(initial, opponents, distant, adjacent));
        assertThat(row(afterAdjacent, 1).category()).isEqualTo("C7");

        var secondC5 = event("S-5", 3, "C5", List.of(
                game("second-c5", 1, List.of(1L, 2L, 3L, 4L), 6, 4, false)));
        var afterSecond = replay.replay(FIRST_DAY, NAMES, List.of(initial, opponents, distant, secondC5));
        assertThat(row(afterSecond, 1).category()).isEqualTo("C5");
        assertThat(row(afterSecond, 1).confirmed()).isTrue();
        assertThat(row(afterSecond, 1).strongerCategories()).isEmpty();
    }

    private List<PreviewEloReplay.Game> winningRun(String prefix, int matches) {
        var games = new ArrayList<PreviewEloReplay.Game>();
        for (int batch = 1; batch <= matches; batch++) {
            games.add(game(prefix + batch, batch, List.of(1L, 2L, 3L, 4L), 6, 4, batch == matches));
        }
        return games;
    }

    private PreviewEloReplay.Event event(String key, int daysLater, String category, List<PreviewEloReplay.Game> games) {
        return new PreviewEloReplay.Event(key, FIRST_DAY.plusDays(daysLater), category, games, Set.of());
    }

    private PreviewEloReplay.Game game(String key, int batch, List<Long> ids, int left, int right, boolean finalMatch) {
        return new PreviewEloReplay.Game(key, batch, ids, left, right, finalMatch);
    }

    private PreviewEloRanking.Row row(PreviewEloRanking.Snapshot snapshot, long id) {
        return snapshot.rows().stream().filter(player -> player.id() == id).findFirst().orElseThrow();
    }
}
