package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.controller.player.PlayerDashboardController;
import com.padle.core.padelcoreservice.controller.view.RankingController;
import com.padle.core.padelcoreservice.rating.EloPilotAccess;
import com.padle.core.padelcoreservice.rating.EloPilotAudience;
import com.padle.core.padelcoreservice.rating.EloRankingView;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
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
import java.util.Map;
import java.util.Optional;

@ControllerAdvice(assignableTypes = {RankingController.class, PlayerDashboardController.class})
@Profile("preview")
@ConditionalOnProperty("app.preview.elo-snapshot")
public class PreviewEloRanking implements EloPilotAudience {
    public record Progress(int tournaments, int matches, int wins, int moreTournaments,
                           int moreMatches, int moreWins) { }

    public record CategoryProgress(String category, Progress progress, boolean eligibleFromOneEvent) { }

    public record Achievements(int tournaments, int matches, int titles, int secondPlaces) { }

    public record TournamentElo(LocalDate day, String category, double elo, double change, int matches, int wins,
                                String title, String format, String resultsUrl) { }

    public record Row(long id, String name, String category, boolean confirmed, BigDecimal elo,
                      int categoryPlace, int categorySize, Integer overallPlace, Progress progress,
                      List<CategoryProgress> strongerCategories, Achievements achievements,
                      List<TournamentElo> tournamentHistory) {
        public Row {
            strongerCategories = strongerCategories == null ? List.of() : List.copyOf(strongerCategories);
            tournamentHistory = tournamentHistory == null ? List.of() : List.copyOf(tournamentHistory);
        }
    }

    public record Snapshot(LocalDate asOf, List<Row> rows) { }

    private final Snapshot snapshot;
    private final Optional<PreviewEloHistory> history;
    private final EloPilotAccess pilot;

    public PreviewEloRanking(ObjectMapper mapper, @Value("${app.preview.elo-snapshot}") Path path,
                             Optional<PreviewEloHistory> history, EloPilotAccess pilot) throws IOException {
        this.history = history;
        this.pilot = pilot;
        try (var input = Files.newInputStream(path)) {
            var imported = mapper.readValue(input, Snapshot.class);
            snapshot = new Snapshot(imported.asOf(), List.copyOf(imported.rows()));
        }
        if (snapshot.asOf() == null || snapshot.rows().isEmpty()) {
            throw new IllegalArgumentException("Нет даты или игроков в срезе Elo");
        }
    }

    @Override
    public boolean allows(Authentication authentication) {
        return pilot.allows(authentication, history.map(PreviewEloHistory::aliases).orElse(Map.of()));
    }

    @ModelAttribute
    public void ranking(Model model, Authentication authentication, HttpServletResponse response) {
        var aliases = history.map(PreviewEloHistory::aliases).orElse(Map.of());
        pilot.protectResponse(response);
        if (!allows(authentication)) {
            return;
        }
        var current = history.map(source -> source.calculate(snapshot.asOf())).orElse(snapshot);
        int startStep = history.map(PreviewEloHistory::startStep).orElse(0);
        EloRankingView.populate(model, current, startStep, aliases);
    }
}
