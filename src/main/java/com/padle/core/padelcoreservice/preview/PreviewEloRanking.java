package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.controller.player.PlayerDashboardController;
import com.padle.core.padelcoreservice.controller.view.RankingController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@ControllerAdvice(assignableTypes = {RankingController.class, PlayerDashboardController.class})
@Profile("preview")
@ConditionalOnProperty("app.preview.elo-snapshot")
public class PreviewEloRanking {
    public record Progress(int tournaments, int matches, int wins, int moreTournaments,
                           int moreMatches, int moreWins) { }

    public record CategoryProgress(String category, Progress progress, boolean eligibleFromOneEvent) { }

    public record Achievements(int tournaments, int matches, int titles, int secondPlaces) { }

    public record Row(long id, String name, String category, boolean confirmed, BigDecimal elo,
                      int categoryPlace, int categorySize, Integer overallPlace, Progress progress,
                      List<CategoryProgress> strongerCategories, Achievements achievements) {
        public Row {
            strongerCategories = strongerCategories == null ? List.of() : List.copyOf(strongerCategories);
        }
    }

    public record Snapshot(LocalDate asOf, List<Row> rows) { }

    private final Snapshot snapshot;
    private final Optional<PreviewEloHistory> history;

    public PreviewEloRanking(ObjectMapper mapper, @Value("${app.preview.elo-snapshot}") Path path,
                             Optional<PreviewEloHistory> history) throws IOException {
        this.history = history;
        try (var input = Files.newInputStream(path)) {
            var imported = mapper.readValue(input, Snapshot.class);
            snapshot = new Snapshot(imported.asOf(), List.copyOf(imported.rows()));
        }
        if (snapshot.asOf() == null || snapshot.rows().isEmpty()) {
            throw new IllegalArgumentException("Нет даты или игроков в срезе Elo");
        }
    }

    @ModelAttribute
    public void ranking(Model model) {
        var current = history.map(source -> source.calculate(snapshot.asOf())).orElse(snapshot);
        model.addAttribute("previewElo", current);
        int startStep = history.map(PreviewEloHistory::startStep).orElse(0);
        model.addAttribute("previewEloStartStep", startStep);
        var categories = current.rows().stream().map(Row::category).distinct();
        model.addAttribute("previewEloCategories", (startStep > 0 ? categories : categories.sorted()).toList());
        model.addAttribute("previewEloByPlayer", current.rows().stream()
                .collect(Collectors.toUnmodifiableMap(Row::id, Function.identity())));
    }
}
