package com.padle.core.padelcoreservice.preview;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EloRefreshSourceTest {
    private final LocalDate cutoff = LocalDate.of(2026, 11, 12);
    private final Map<Long, String> names = Map.of(1L, "A", 2L, "B", 3L, "C", 4L, "D", 5L, "Alias");
    private final PreviewEloReplay replay = new PreviewEloReplay(40);

    private PreviewEloReplay.Event event(String key, int days, int lastLeft, int lastRight) {
        var games = IntStream.rangeClosed(1, 5).mapToObj(i -> new PreviewEloReplay.Game(key + ":" + i, i,
                List.of(1L, 2L, 3L, 4L), i == 5 ? lastLeft : 6, i == 5 ? lastRight : 4, i == 5)).toList();
        return new PreviewEloReplay.Event(key, cutoff.plusDays(days), "C5", games, Set.of(), Set.of(),
                "Final", "pair", "/torneo/" + key.substring(2));
    }

    private EloRefreshSource source(Set<String> managed) {
        return new EloRefreshSource(new PreviewEloReplay.History(names, List.of(event("S-1", 0, 6, 4)),
                Map.of(5L, 1L)), cutoff, managed, Set.of("S-99"));
    }

    private PreviewEloRanking.Snapshot calculate(EloRefreshSource source, PreviewEloReplay.Event... events) {
        var merged = source.merge(new PreviewEloLiveEvents.LiveHistory(List.of(events), names));
        return replay.replay(cutoff, merged.names(), merged.events());
    }

    private PreviewEloRanking.Row player(PreviewEloRanking.Snapshot snapshot) {
        return snapshot.rows().stream().filter(row -> row.id() == 1).findFirst().orElseThrow();
    }

    @Test
    void newEventIsAddedOnceAndChangingOldFinalReplaysLaterHistory() {
        var source = source(Set.of("S-1"));
        var first = event("S-1", 0, 6, 4);
        var later = event("S-2", 1, 4, 6);
        var original = calculate(source, first, later);
        assertThat(player(original).achievements().matches()).isEqualTo(10);
        assertThat(player(original).achievements().titles()).isEqualTo(1);
        assertThat(calculate(source, first, later)).isEqualTo(original);
        var corrected = calculate(source, event("S-1", 0, 4, 6), later);
        assertThat(player(corrected).elo()).isLessThan(player(original).elo());
        assertThat(player(corrected).achievements().titles()).isZero();
        assertThat(player(corrected).tournamentHistory().get(1).change())
                .isNotEqualTo(player(original).tournamentHistory().get(1).change());
        assertThat(calculate(source, first, later)).isEqualTo(original);
    }

    @Test
    void ignoresUnmanagedAndWithheldOldEventsAndExcludedFixtures() {
        var source = source(Set.of());
        var baseline = calculate(source);
        assertThat(calculate(source, event("S-1", 0, 4, 6), event("S-3", -1, 6, 4), event("S-99", 2, 6, 4)))
                .isEqualTo(baseline);
    }

    @Test
    void missingManagedEventIsRemovedAndRestorationReturnsBaseline() {
        var source = source(Set.of("S-1"));
        assertThat(calculate(source).rows()).isEmpty();
        assertThat(calculate(source, event("S-1", 0, 6, 4)))
                .isEqualTo(calculate(source(Set.of())));
    }

    @Test
    void normalizesAliasAndPreservesVerifiedDateAndCategory() {
        var source = source(Set.of("S-1"));
        var nativeEvent = new PreviewEloReplay.Event("S-1", cutoff.plusDays(5), "C9",
                List.of(new PreviewEloReplay.Game("pair:100", 1, List.of(5L, 2L, 3L, 4L), 6, 4, true)), Set.of());
        var merged = source.merge(new PreviewEloLiveEvents.LiveHistory(List.of(nativeEvent), names));
        assertThat(merged.events()).hasSize(1);
        assertThat(merged.events().get(0).category()).isEqualTo("C5");
        assertThat(merged.events().get(0).day()).isEqualTo(cutoff);
        assertThat(merged.events().get(0).games().get(0).ids()).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void rejectsDuplicateEventsAndMatchesAndInvalidSourceBoundaries() {
        assertThatThrownBy(() -> calculate(source(Set.of()), event("S-2", 1, 6, 4), event("S-2", 1, 6, 4)))
                .isInstanceOf(IllegalArgumentException.class);
        for (var managed : List.of(Set.of("S-44"), Set.of("archive:1"))) {
            assertThatThrownBy(() -> source(managed)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new EloRefreshSource(source(Set.of()).history(), cutoff.minusDays(1), Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EloRefreshSource(source(Set.of()).history(), cutoff, Set.of(), Set.of("S-1")))
                .isInstanceOf(IllegalArgumentException.class);
        var a = new PreviewEloReplay.Event("S-2", cutoff.plusDays(1), "C5",
                List.of(new PreviewEloReplay.Game("pair:100", 1, List.of(1L, 2L, 3L, 4L), 6, 4, false)), Set.of());
        var b = new PreviewEloReplay.Event("S-3", cutoff.plusDays(2), "C5",
                List.of(new PreviewEloReplay.Game("americano:100", 1, List.of(1L, 2L, 3L, 4L), 6, 4, false)), Set.of());
        assertThatThrownBy(() -> calculate(source(Set.of()), a, b)).isInstanceOf(IllegalArgumentException.class);
    }
}
