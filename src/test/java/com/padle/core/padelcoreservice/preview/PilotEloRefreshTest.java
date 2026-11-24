package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.rating.EloPilotProperties;
import com.padle.core.padelcoreservice.rating.EloPilotSnapshot;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
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

    @ParameterizedTest
    @CsvSource({"C5,C5,1632,1608", "C4,—,1512,1488"})
    void acceptsFrozenSnapshotBeforeTheC3ScaleWithoutChangingItsData(String eventLevel, String category,
                                                                   int winnerRating, int loserRating) throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var day = LocalDate.of(2026, 11, 12);
        var game = new PreviewEloReplay.Game("pair:1", 1, List.of(1L, 2L, 3L, 4L), 6, 4, false);
        var event = new PreviewEloReplay.Event("S-1", day, eventLevel, List.of(game),
                Set.of(), Set.of(), "Legacy", "pair", null);
        var names = Map.of(1L, "A", 2L, "B", 3L, "C", 4L, "D");
        var history = new PreviewEloReplay.History(names, List.of(event));
        var historyPath = directory.resolve("legacy-history.json");
        var snapshotPath = directory.resolve("legacy-snapshot.json");
        Files.writeString(historyPath, mapper.writeValueAsString(new EloRefreshSource(history, day, Set.of(), Set.of())));
        var rows = List.of(1L, 2L, 3L, 4L).stream().map(id -> {
            boolean winner = id <= 2;
            int rating = winner ? winnerRating : loserRating;
            return new PreviewEloRanking.Row(id, names.get(id), category, false, BigDecimal.valueOf((double) rating),
                    winner ? 1 : 3, 4, winner ? 1 : 3,
                    eventLevel.equals("C5") ? new PreviewEloRanking.Progress(1, 1, winner ? 1 : 0, 1, 5, winner ? 2 : 3) : null, List.of(),
                    new PreviewEloRanking.Achievements(1, 1, 0, 0),
                    List.of(new PreviewEloRanking.TournamentElo(day, eventLevel, rating, winner ? 12 : -12,
                            1, winner ? 1 : 0, "Legacy", "pair", null)));
        }).toList();
        var frozen = new PreviewEloRanking.Snapshot(day, rows);
        Files.writeString(snapshotPath, mapper.writeValueAsString(new EloPilotSnapshot(frozen, 40, Map.of())));
        byte[] original = Files.readAllBytes(snapshotPath);
        var properties = new EloPilotProperties(true, Set.of(1L), snapshotPath);
        var jdbc = mock(JdbcClient.class);
        var king = mock(KingOfCourtService.class);
        var americano = mock(AmericanoService.class);
        var playoff = mock(TeamPlayoffService.class);
        assertThatCode(() -> new PilotEloRefresh(mapper, properties, historyPath, jdbc, king, americano, playoff))
                .doesNotThrowAnyException();
        assertThat(Files.readAllBytes(snapshotPath)).isEqualTo(original);
        Files.writeString(snapshotPath, mapper.writeValueAsString(new EloPilotSnapshot(frozen, 40, Map.of(), true)));
        assertThatThrownBy(() -> new PilotEloRefresh(mapper, properties, historyPath, jdbc, king, americano, playoff))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("не воспроизводит");
        verifyNoInteractions(jdbc, king, americano, playoff);
    }

    @Test
    void markedTestTournamentDisappearsFromReplayedHistoryAndCanBeRestored() {
        var day = LocalDate.of(2026, 11, 14);
        var game = new PreviewEloReplay.Game("pair:1", 1, List.of(1L, 2L, 3L, 4L), 6, 4, false);
        var event = new PreviewEloReplay.Event("S-123", day, "C8", List.of(game), Set.of(), Set.of(), "Test", "pair", null);
        var history = new PreviewEloReplay.History(Map.of(1L, "A", 2L, "B", 3L, "C", 4L, "D"), List.of(event));
        var replay = new PreviewEloReplay(40, true);
        assertThat(replay.replay(day, history.names(), history.events()).rows()).hasSize(4);
        var excluded = PilotEloRefresh.excludeTestTournaments(history, Set.of(123L));
        assertThat(replay.replay(day, excluded.names(), excluded.events()).rows()).isEmpty();
        var restored = PilotEloRefresh.excludeTestTournaments(history, Set.of());
        assertThat(replay.replay(day, restored.names(), restored.events()).rows()).hasSize(4);
    }

    @Test
    void correctsQuarterfinalToConfirmedPlayerBeforeEloReplay() {
        var day = LocalDate.of(2026, 6, 14);
        var played = new PreviewEloReplay.Game("other", 1, List.of(166L, 407L, 225L, 221L), 6, 4, false);
        var disputed = new PreviewEloReplay.Game("B-1297006686:N4:N5", 4,
                List.of(166L, 461L, 225L, 221L), 2, 6, false);
        var event = new PreviewEloReplay.Event("B-1297006686", day, "D7", List.of(played, disputed),
                Set.of(), Set.of(), "Tournament", "pair", null);
        var history = new PreviewEloReplay.History(Map.of(166L, "Yulia", 407L, "Irina", 225L, "Tony", 221L, "Arina", 461L, "Alesia"), List.of(event));
        var corrected = PilotEloRefresh.correctJuneQuarterfinal(history);
        assertThat(corrected.events().get(0).games()).containsExactly(played,
                new PreviewEloReplay.Game("B-1297006686:N4:N5", 4, List.of(166L, 407L, 225L, 221L), 2, 6, false));
        assertThat(new PreviewEloReplay(40, true).replay(day, corrected.names(), corrected.events()).rows())
                .extracting(PreviewEloRanking.Row::id).doesNotContain(461L).contains(166L, 407L, 225L, 221L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"C8", "C5", "C4", "C3"})
    void weightedRefreshRequiresMatchingWeightedSnapshotBeforeDatabaseRead(String level) throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var day = LocalDate.of(2026, 11, 12);
        var game = new PreviewEloReplay.Game("pair:1", 1, List.of(1L, 2L, 3L, 4L), 6, 4, false);
        var event = new PreviewEloReplay.Event("S-1", day, level, List.of(game),
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
