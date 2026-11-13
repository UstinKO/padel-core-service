package com.padle.core.padelcoreservice.preview;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewEloCategoryTest {
    private final PreviewEloReplay replay = new PreviewEloReplay(40);
    private final Map<Long, String> names = Map.of(1L, "A", 2L, "B", 3L, "C", 4L, "D");
    private final LocalDate day = LocalDate.of(2026, 11, 14);

    private PreviewEloReplay.Event event(String category, int id, int matches) {
        var games = IntStream.rangeClosed(1, matches).mapToObj(i -> new PreviewEloReplay.Game(
                "king:" + id + ":" + i, i, List.of(1L, 2L, 3L, 4L), 11, 10, false)).toList();
        return new PreviewEloReplay.Event("S-" + id, day.plusDays(id), category, games, Set.of(), Set.of(),
                "Исходное название турнира", "king", "/torneo/" + id + "/king-of-court");
    }

    @ParameterizedTest
    @CsvSource({"C9_C8,C9,1472", "C8_C7,C8,1512", "C7_C6,C7,1552", "C6_C5,C6,1592",
            "C5_C4,C5,1632", "D9_D8,D9,1432", "D7_D8,D8,1472", "D7_D6,D7,1512"})
    void rangeAndSingleCategoryGiveIdenticalStartProgressAndConfirmation(String range, String single, int elo) {
        for (var label : List.of(range, range.replace('_', '/'))) {
            var first = event(label, 1, 1);
            var baseline = event(single, 1, 1);
            assertThat(first).isEqualTo(baseline);
            var afterFirst = replay.replay(day, names, List.of(first));
            var player = afterFirst.rows().stream().filter(row -> row.id() == 1).findFirst().orElseThrow();
            assertThat(player.elo()).isEqualByComparingTo(BigDecimal.valueOf(elo));
            assertThat(player.category()).isNotEqualTo("—");
            assertThat(player.confirmed()).isFalse();
            var actual = replay.replay(day, names, List.of(event(label, 1, 3), event(label, 2, 3)));
            var expected = replay.replay(day, names, List.of(event(single, 1, 3), event(single, 2, 3)));
            assertThat(actual).isEqualTo(expected);
            assertThat(actual.rows().stream().filter(row -> row.id() == 1).findFirst().orElseThrow().confirmed()).isTrue();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"SUMA_14", "TODOS", "D", "C7_C5", "C7/C5", "C5_UNKNOWN", "D6_D5"})
    void doesNotInferUnsupportedCategories(String category) {
        var event = event(category, 1, 6);
        assertThat(event.category()).isEqualTo(category);
        var result = replay.replay(day, names, List.of(event));
        assertThat(result.rows()).allMatch(row -> row.category().equals("—") && !row.confirmed());
    }
}
