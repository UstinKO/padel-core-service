package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.controller.player.PlayerDashboardController;
import com.padle.core.padelcoreservice.controller.view.RankingController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ControllerAdvice(assignableTypes = {RankingController.class, PlayerDashboardController.class})
@Profile("preview")
@ConditionalOnProperty("app.preview.elo-snapshot")
public class PreviewEloRanking {
    public record Progress(int tournaments, int matches, int wins, int moreTournaments,
                           int moreMatches, int moreWins) { }

    public record Row(long id, String name, String category, boolean confirmed, BigDecimal elo,
                      int categoryPlace, int categorySize, Integer overallPlace, Progress progress) { }

    public record Snapshot(LocalDate asOf, List<Row> rows) { }

    private final Snapshot snapshot;
    private final List<String> categories;
    private final Map<Long, Row> byPlayerId;

    public PreviewEloRanking(ObjectMapper mapper, @Value("${app.preview.elo-snapshot}") Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            var imported = mapper.readValue(input, Snapshot.class);
            snapshot = new Snapshot(imported.asOf(), List.copyOf(imported.rows()));
        }
        if (snapshot.asOf() == null || snapshot.rows().isEmpty()) {
            throw new IllegalArgumentException("Нет даты или игроков в срезе Elo");
        }
        categories = snapshot.rows().stream().map(Row::category).distinct().toList();
        byPlayerId = snapshot.rows().stream()
                .collect(Collectors.toUnmodifiableMap(Row::id, Function.identity()));
    }

    @ModelAttribute("previewElo")
    public Snapshot snapshot() {
        return snapshot;
    }

    @ModelAttribute("previewEloCategories")
    public List<String> categories() {
        return categories;
    }

    @ModelAttribute("previewEloByPlayer")
    public Map<Long, Row> byPlayerId() {
        return byPlayerId;
    }
}
