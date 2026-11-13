package com.padle.core.padelcoreservice.rating;

import com.padle.core.padelcoreservice.controller.view.RankingController;
import com.padle.core.padelcoreservice.preview.PreviewEloRanking;
import com.padle.core.padelcoreservice.service.RankingService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PilotEloRankingTest {
    @TempDir Path directory;
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final RankingService legacy = mock(RankingService.class);

    private PreviewEloRanking.Row row(long id) {
        return new PreviewEloRanking.Row(id, "Player " + id, "C7", true, BigDecimal.valueOf(1540),
                (int) id, 2, (int) id, null, List.of(), null, List.of());
    }

    private EloPilotSnapshot bundle() {
        return new EloPilotSnapshot(new PreviewEloRanking.Snapshot(LocalDate.of(2026, 11, 13),
                List.of(row(1), row(2))), 40, Map.of(8L, 1L));
    }

    private Path writeBundle() throws Exception {
        var path = directory.resolve("pilot.json");
        Files.writeString(path, mapper.writeValueAsString(bundle()));
        return path;
    }

    private MockMvc web(boolean enabled, Set<Long> ids) throws Exception {
        var properties = new EloPilotProperties(enabled, ids, writeBundle());
        var advice = new PilotEloRanking(mapper, properties, new EloPilotAccess(properties));
        return MockMvcBuilders.standaloneSetup(new RankingController(legacy, mapper))
                .setControllerAdvice(advice)
                .setSingleView((model, request, response) -> response.getWriter()
                        .write(model.containsKey("previewElo") ? "pilot" : "legacy"))
                .build();
    }

    @Test
    void selectedPlayerGetsWholeRankingAndAliasesShareOneRow() throws Exception {
        var mvc = web(true, Set.of(1L));
        var result = mvc.perform(get("/ranking").principal(EloPilotAccessTest.player(8)))
                .andExpect(status().isOk()).andExpect(content().string("pilot"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andReturn();
        var model = result.getModelAndView().getModel();
        assertThat(((PreviewEloRanking.Snapshot) model.get("previewElo")).rows()).hasSize(2);
        var byPlayer = (Map<?, ?>) model.get("previewEloByPlayer");
        assertThat(byPlayer.get(8L)).isSameAs(byPlayer.get(1L));
        assertThat(result.getResponse().getHeaders("Vary")).contains("Cookie", "Authorization");
        verifyNoInteractions(legacy);
    }

    @Test
    void outsiderCannotEnablePilotByChangingRequestParametersCookiesOrHeaders() throws Exception {
        web(true, Set.of(1L)).perform(get("/ranking").principal(EloPilotAccessTest.player(2))
                        .param("playerId", "1").param("pilot", "true").param("previewElo", "true")
                        .cookie(new Cookie("playerId", "1")).header("X-Player-ID", "1"))
                .andExpect(content().string("legacy"))
                .andExpect(model().attributeDoesNotExist("previewElo", "previewEloByPlayer", "previewEloCategories"))
                .andExpect(header().string("Cache-Control", "private, no-store"));
        verify(legacy).getRankingCompleto();
    }

    @Test
    void anonymousEmptyAndDisabledPilotUseLegacyPage() throws Exception {
        web(true, Set.of(1L)).perform(get("/ranking")).andExpect(content().string("legacy"));
        web(true, Set.of()).perform(get("/ranking").principal(EloPilotAccessTest.player(1)))
                .andExpect(content().string("legacy"));
        web(false, Set.of(1L)).perform(get("/ranking").principal(EloPilotAccessTest.player(1)))
                .andExpect(content().string("legacy"));
    }

    @Test
    void ordinaryProfileRequiresExplicitEnableAndPreviewDoesNotLoadFileAdapter() throws Exception {
        var runner = new ApplicationContextRunner().withUserConfiguration(EloPilotAccess.class, PilotEloRanking.class)
                .withBean(ObjectMapper.class, () -> mapper);
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(PilotEloRanking.class));
        var path = writeBundle();
        runner.withPropertyValues("app.rating.pilot.enabled=true", "app.rating.pilot.player-ids=1",
                        "app.rating.pilot.snapshot=" + path)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(PilotEloRanking.class));
        runner.withPropertyValues("app.rating.pilot.enabled=true", "spring.profiles.active=preview")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(PilotEloRanking.class));
        runner.withPropertyValues("app.rating.pilot.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void refusesDuplicateRowsAndInvalidAliasTargets() {
        assertThatThrownBy(() -> new EloPilotSnapshot(new PreviewEloRanking.Snapshot(
                LocalDate.now(), List.of(row(1), row(1))), 40, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        for (var aliases : List.of(Map.of(1L, 1L), Map.of(8L, 9L), Map.of(8L, 9L, 9L, 1L))) {
            assertThatThrownBy(() -> new EloPilotSnapshot(bundle().snapshot(), 40, aliases))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
