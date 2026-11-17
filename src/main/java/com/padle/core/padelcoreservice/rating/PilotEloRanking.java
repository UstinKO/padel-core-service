package com.padle.core.padelcoreservice.rating;

import com.padle.core.padelcoreservice.controller.player.PerfilController;
import com.padle.core.padelcoreservice.controller.player.PlayerDashboardController;
import com.padle.core.padelcoreservice.controller.view.RankingController;
import com.padle.core.padelcoreservice.preview.PilotEloRefresh;
import com.padle.core.padelcoreservice.preview.PreviewEloRanking;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@ControllerAdvice(assignableTypes = {RankingController.class, PlayerDashboardController.class, PerfilController.class})
@Profile("!preview")
@ConditionalOnProperty(name = "app.rating.pilot.enabled", havingValue = "true")
public class PilotEloRanking implements EloPilotAudience {
    @Value("${app.rating.pilot.all-players:false}")
    private boolean allPlayers;
    private final EloPilotAccess access;
    private final EloPilotSnapshot snapshot;
    private final Supplier<PreviewEloRanking.Snapshot> currentSnapshot;
    private final boolean formatWeighted;

    public PilotEloRanking(ObjectMapper mapper, EloPilotProperties properties, EloPilotAccess access,
                           Optional<PilotEloRefresh> refresh) throws IOException {
        this.access = access;
        var path = Objects.requireNonNull(properties.snapshot(), "Для пилота нужен проверенный срез рейтинга");
        try (var input = Files.newInputStream(path)) {
            snapshot = mapper.readValue(input, EloPilotSnapshot.class);
        }
        formatWeighted = refresh.map(PilotEloRefresh::isFormatWeighted).orElse(snapshot.formatWeighted());
        currentSnapshot = refresh.<Supplier<PreviewEloRanking.Snapshot>>map(service -> service::calculate)
                .orElse(snapshot::snapshot);
    }

    @PostConstruct
    void verifyPublicFormula() {
        if (allPlayers && !formatWeighted) {
            throw new IllegalStateException("Для общего доступа необходима новая формула рейтинга");
        }
    }

    @Override
    public boolean allows(Authentication authentication) {
        return access.allows(authentication, snapshot.aliases());
    }

    @ModelAttribute
    public void ranking(Model model, Authentication authentication, HttpServletResponse response, HttpServletRequest request) {
        access.protectResponse(response);
        if (allows(authentication) || (allPlayers && "/ranking".equals(request.getRequestURI()))) {
            EloRankingView.populate(model, currentSnapshot.get(),
                    snapshot.startStep(), snapshot.aliases());
            model.addAttribute("eloFormatWeighted", formatWeighted);
            model.addAttribute("eloFeedbackAllowed", allows(authentication));
        }
    }
}
