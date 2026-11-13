package com.padle.core.padelcoreservice.preview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ui.ExtendedModelMap;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreviewEloIdentityViewTest {
    @Test
    void bothLinkedCabinetsSeeOneRowWhileLeaderboardHasNoDuplicate(@TempDir Path directory) throws Exception {
        var date = LocalDate.of(2026, 11, 13);
        var game = new PreviewEloReplay.Game("match", 1, List.of(8L, 2L, 3L, 4L), 6, 4, false);
        var history = new PreviewEloReplay.History(Map.of(1L, "Main", 8L, "Duplicate", 2L, "Two",
                3L, "Three", 4L, "Four"),
                List.of(new PreviewEloReplay.Event("S-1", date, "C7", List.of(game), Set.of())), Map.of(8L, 1L));
        var snapshot = new PreviewEloReplay(40).replay(date, history.names(), history.events());
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var file = directory.resolve("snapshot.json");
        Files.writeString(file, mapper.writeValueAsString(snapshot));
        var source = mock(PreviewEloHistory.class);
        when(source.calculate(date)).thenReturn(snapshot);
        when(source.aliases()).thenReturn(history.aliases());
        var view = new PreviewEloRanking(mapper, file, Optional.of(source));
        var model = new ExtendedModelMap();
        view.ranking(model);
        var players = (Map<?, ?>) model.get("previewEloByPlayer");
        assertThat(players.get(8L)).isSameAs(players.get(1L)).isNotNull();
        assertThat(((PreviewEloRanking.Snapshot) model.get("previewElo")).rows())
                .hasSize(4).noneMatch(row -> row.id() == 8L);
    }
}
