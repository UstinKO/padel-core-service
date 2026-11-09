package com.padle.core.padelcoreservice.controller.view;

import com.padle.core.padelcoreservice.repository.marketing.QrStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key"
})
class RebyQrControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private QrStatsRepository repository;

    @BeforeEach
    void clearCounts() {
        jdbc.update("DELETE FROM marketing_qr_daily_db");
    }

    @Test
    void publicLinksKeepFixedTargetsAndSeparateCounters() throws Exception {
        mvc.perform(get("/qr/reby/site").param("url", "https://evil.example")
                        .param("utm_source", "external"))
                .andExpect(status().isFound()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(redirectedUrl("https://1-padel.com/?utm_source=reby&utm_medium=qr&utm_campaign=offline_tournaments"));
        mvc.perform(get("/qr/reby/telegram").param("redirect", "https://evil.example"))
                .andExpect(status().isFound()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(redirectedUrl("https://t.me/padelarg"));
        mvc.perform(get("/qr/reby/site").with(user("player").roles("PLAYER")))
                .andExpect(status().isFound());
        mvc.perform(get("/api/admin/marketing/qr-stats").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("reby"))
                .andExpect(jsonPath("$.site").value(2)).andExpect(jsonPath("$.telegram").value(1));
    }

    @Test
    void probesAndPreviewsDoNotIncrement() throws Exception {
        mvc.perform(head("/qr/reby/site")).andExpect(status().isFound());
        mvc.perform(get("/qr/reby/telegram").header("User-Agent", "TelegramBot"))
                .andExpect(status().isFound());
        mvc.perform(get("/qr/reby/site").header("Sec-Purpose", "prefetch"))
                .andExpect(status().isFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM marketing_qr_daily_db", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLAYER", "OWNER", "ORGANIZER", "ADMIN", "CLUB_ADMIN"})
    void reportForbiddenForOtherRoles(String role) throws Exception {
        mvc.perform(get("/api/admin/marketing/qr-stats").with(user("other").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reportNotPublicAndUnknownQrNotPublic() throws Exception {
        mvc.perform(get("/api/admin/marketing/qr-stats")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/qr/reby/unknown")).andExpect(status().is3xxRedirection());
    }

    @Test
    void reportValidatesDatesAndReturnsZeroForEmptyRange() throws Exception {
        mvc.perform(get("/api/admin/marketing/qr-stats").param("from", "2026-11-01")
                        .param("to", "2026-11-02").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.site").value(0))
                .andExpect(jsonPath("$.telegram").value(0)).andExpect(jsonPath("$.days.length()").value(0L));
        mvc.perform(get("/api/admin/marketing/qr-stats").param("from", "2026-11-02")
                        .param("to", "2026-11-01").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/marketing/qr-stats").param("from", "not-a-date")
                        .with(user("admin").roles("SUPER_ADMIN"))).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentClicksAreDurableAndDaysRemainSeparate() throws Exception {
        LocalDate date = LocalDate.of(2026, 11, 1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var futures = IntStream.range(0, 40).mapToObj(i -> executor.submit(() ->
                    repository.increment(date, "site"))).toList();
            for (var future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
        repository.increment(date.plusDays(1), "telegram");
        var persisted = new QrStatsRepository(jdbc).findDays(date, date.plusDays(1));
        assertThat(persisted).hasSize(2);
        assertThat(persisted.get(0).site()).isEqualTo(40);
        assertThat(persisted.get(0).telegram()).isZero();
        assertThat(persisted.get(1).telegram()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"black_padel", "15cero_villa_crespo", "15cero_saavedra"})
    void printedLandingCountsOwnSource(String source) throws Exception {
        mvc.perform(get("/").param("utm_source", source).param("utm_medium", "qr")
                        .param("utm_campaign", "offline_tournaments"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/admin/marketing/qr-summary").param("source", source)
                        .with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sources[0].source").value(source))
                .andExpect(jsonPath("$.sources[0].site").value(1))
                .andExpect(jsonPath("$.sources[0].telegram").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM marketing_qr_daily_db", Long.class)).isEqualTo(1);
    }

    @Test
    void ordinaryPagesWrongCampaignsProbesAndRebyLandingDoNotAddCounts() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        for (String source : new String[]{"unknown", "reby"}) {
            mvc.perform(get("/").param("utm_source", source).param("utm_medium", "qr")
                    .param("utm_campaign", "offline_tournaments")).andExpect(status().isOk());
        }
        mvc.perform(get("/").param("utm_source", "black_padel").param("utm_medium", "social")
                .param("utm_campaign", "offline_tournaments")).andExpect(status().isOk());
        mvc.perform(get("/").param("utm_source", "black_padel").param("utm_medium", "qr")
                .param("utm_campaign", "other")).andExpect(status().isOk());
        mvc.perform(head("/").param("utm_source", "black_padel").param("utm_medium", "qr")
                .param("utm_campaign", "offline_tournaments")).andExpect(status().isOk());
        mvc.perform(get("/").param("utm_source", "black_padel").param("utm_medium", "qr")
                .param("utm_campaign", "offline_tournaments").header("User-Agent", "Googlebot"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM marketing_qr_daily_db", Long.class)).isZero();
        mvc.perform(get("/qr/reby/site")).andExpect(status().isFound());
        mvc.perform(get("/").param("utm_source", "reby").param("utm_medium", "qr")
                .param("utm_campaign", "offline_tournaments")).andExpect(status().isOk());
        assertThat(repository.findDays(LocalDate.now().minusDays(1), LocalDate.now().plusDays(1)).get(0).site()).isEqualTo(1);
    }

    @Test
    void summarySeparatesSourcesAndPreservesOldReport() throws Exception {
        LocalDate day = LocalDate.of(2026, 11, 1);
        repository.increment(day, "site");
        repository.increment(day, "black_padel", "site");
        repository.increment(day, "15cero_saavedra", "site");
        mvc.perform(get("/api/admin/marketing/qr-summary").param("from", "2026-11-01")
                        .param("to", "2026-11-01").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sources.length()").value(4L))
                .andExpect(jsonPath("$.sources[0].site").value(1))
                .andExpect(jsonPath("$.sources[1].site").value(0))
                .andExpect(jsonPath("$.sources[2].site").value(1))
                .andExpect(jsonPath("$.sources[3].site").value(1));
        mvc.perform(get("/api/admin/marketing/qr-summary").param("source", "evil")
                        .with(user("admin").roles("SUPER_ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/marketing/qr-summary").param("from", "2026-11-02")
                        .param("to", "2026-11-01").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/marketing/qr-summary")).andExpect(status().is3xxRedirection());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLAYER", "OWNER", "ORGANIZER", "CLUB_ADMIN"})
    void summaryForbiddenForOtherRoles(String role) throws Exception {
        mvc.perform(get("/api/admin/marketing/qr-summary").with(user("other").roles(role)))
                .andExpect(status().isForbidden());
    }
}
