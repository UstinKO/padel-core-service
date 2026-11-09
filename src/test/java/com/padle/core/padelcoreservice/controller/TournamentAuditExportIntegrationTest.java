package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class TournamentAuditExportIntegrationTest {

    private static final String URL = "/admin/export/tournament-audit";

    @Autowired private MockMvc mockMvc;
    @Autowired private PlayerRepository players;

    @Test
    void redirectsAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get(URL)).andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"OWNER", "ORGANIZER", "CLUB_ADMIN", "ADMIN", "PLAYER"})
    void deniesOtherRoles(String role) throws Exception {
        mockMvc.perform(get(URL).with(user("user@example.test").roles(role)))
                .andExpect(redirectedUrl("/error/403"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    @Transactional
    void downloadsAuditWithIdsAndNoPersonalFields() throws Exception {
        PlayerPadel guest = players.saveAndFlush(PlayerPadel.builder()
                .nombre("AuditPrivateFirstName")
                .apellido("AuditPrivateLastName")
                .email("audit-private-email@1padel.guest")
                .telefono("+541155550884")
                .telegramUsername("audit_private_telegram")
                .passwordHash("audit-private-password-hash")
                .activo(true)
                .preferredLocale("es")
                .nivelJugador(Nivel.C7)
                .build());

        mockMvc.perform(get(URL).with(user("audit@example.test").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"1padel-tournament-audit.json\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(result -> {
                    var body = result.getResponse().getContentAsString();
                    var export = JsonMapper.builder().build().readTree(body);
                    assertThat(export.path("schemaVersion").asInt()).isEqualTo(1);
                    assertThat(export.path("americanoMatches").isArray()).isTrue();
                    assertThat(export.path("versionKingMatches").isArray()).isTrue();
                    assertThat(export.path("versionAmericanoMatches").isArray()).isTrue();
                    var profiles = export.path("profiles");
                    assertThat(StreamSupport.stream(profiles.spliterator(), false))
                            .anyMatch(profile -> profile.path("id").asLong() == guest.getId()
                                    && profile.path("guest").asBoolean());
                    assertThat(body).doesNotContain("AuditPrivateFirstName", "AuditPrivateLastName",
                            "audit-private-email", "541155550884", "audit_private_telegram",
                            "audit-private-password-hash");
                });
    }
}
