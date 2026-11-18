package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.rating.EloPilotProperties;
import com.padle.core.padelcoreservice.rating.EloPilotSnapshot;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PilotEloRefreshTest {
    @TempDir Path directory;

    @Test
    void excludesVerifiedQaTournamentAlongsideConfiguredExclusions() {
        assertThat(PilotEloRefresh.excludedEvents(Set.of("S-172")))
                .containsExactlyInAnyOrder("S-172", "S-189")
                .doesNotContain("S-188", "S-190");
    }

    @Test
    void weightedRefreshRequiresMatchingWeightedSnapshotBeforeDatabaseRead() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var day = LocalDate.of(2026, 11, 12);
        var game = new PreviewEloReplay.Game("pair:1", 1, List.of(1L, 2L, 3L, 4L), 6, 4, false);
        var event = new PreviewEloReplay.Event("S-1", day, "C8", List.of(game),
                Set.of(), Set.of(), "Pairs", "pair", null);
        var history = new PreviewEloReplay.History(Map.of(1L, "A", 2L, "B", 3L, "C", 4L, "D"), List.of(event));
        var source = new EloRefreshSource(history, day, Set.of(), Set.of());
        var historyPath = directory.resolve("weighted-history.json");
        var snapshotPath = directory.resolve("weighted-snapshot.json");
        Files.writeString(historyPath, mapper.writeValueAsString(source));
        var weighted = new PreviewEloReplay(40, true).replay(day, history.names(), history.events());
        Files.writeString(snapshotPath, mapper.writeValueAsString(new EloPilotSnapshot(weighted, 40, Map.of(), true)));
        var properties = new EloPilotProperties(true, Set.of(1L), snapshotPath);
        var jdbc = mock(JdbcClient.class);
        var king = mock(KingOfCourtService.class);
        var americano = mock(AmericanoService.class);
        var playoff = mock(TeamPlayoffService.class);
        assertThatCode(() -> new PilotEloRefresh(mapper, properties, historyPath, jdbc, king, americano, playoff))
                .doesNotThrowAnyException();
        Files.writeString(snapshotPath, mapper.writeValueAsString(new EloPilotSnapshot(weighted, 40, Map.of(), false)));
        assertThatThrownBy(() -> new PilotEloRefresh(mapper, properties, historyPath, jdbc, king, americano, playoff))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("не воспроизводит");
        verifyNoInteractions(jdbc, king, americano, playoff);
    }

    @Test
    void requiresHistoryToReproduceSnapshotBeforeAnyDatabaseRead() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var day = LocalDate.of(2026, 11, 12);
        var game = new PreviewEloReplay.Game("americano:1", 1, List.of(1L, 2L, 3L, 4L), 6, 4, false);
        var history = new PreviewEloReplay.History(Map.of(1L, "A", 2L, "B", 3L, "C", 4L, "D"),
                List.of(new PreviewEloReplay.Event("S-1", day, "C7", List.of(game), Set.of())));
        var source = new EloRefreshSource(history, day, Set.of(), Set.of());
        var historyPath = directory.resolve("history.json");
        Files.writeString(historyPath, mapper.writeValueAsString(source));
        var snapshotPath = directory.resolve("snapshot.json");
        var snapshot = new PreviewEloReplay(40).replay(day, history.names(), history.events());
        Files.writeString(snapshotPath, mapper.writeValueAsString(new EloPilotSnapshot(snapshot, 40, Map.of())));
        var properties = new EloPilotProperties(true, Set.of(1L), snapshotPath);
        var jdbc = mock(JdbcClient.class);
        var king = mock(KingOfCourtService.class);
        var americano = mock(AmericanoService.class);
        var playoff = mock(TeamPlayoffService.class);
        assertThatCode(() -> new PilotEloRefresh(mapper, properties, historyPath, jdbc, king, americano, playoff))
                .doesNotThrowAnyException();
        assertThat(new PilotEloRefresh(mapper, properties, historyPath, jdbc, king, americano, playoff, true).isFormatWeighted()).isTrue();
        Files.writeString(snapshotPath, mapper.writeValueAsString(new EloPilotSnapshot(snapshot, 80, Map.of())));
        assertThatThrownBy(() -> new PilotEloRefresh(mapper, properties, historyPath, jdbc, king, americano, playoff))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("не воспроизводит");
        verifyNoInteractions(jdbc, king, americano, playoff);
    }
}
