package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.persistence.identity.PlayerRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.application.tournament.Nivel;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;

import static com.padle.core.padelcoreservice.support.PlayerFixtures.principal;
import static com.padle.core.padelcoreservice.support.PlayerFixtures.playerUser;
import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        "recaptcha.secret-key=test-secret-key",
        "test.context-id=spa-session-contract"
})
class SpaSessionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlayerRepository players;

    @Test
    void anonymousSessionHasExactSafeShapeAndCsrfCookie() throws Exception {
        var result = mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.userType").value(nullValue()))
                .andExpect(jsonPath("$.id").value(nullValue()))
                .andExpect(jsonPath("$.fullName").value(nullValue()))
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.clubId").value(nullValue()))
                .andExpect(jsonPath("$.hasValidContact").value(nullValue()))
                .andExpect(jsonPath("$.preferredLocale").value(nullValue()))
                .andReturn();

        assertThat(result.getResponse().getCookie("XSRF-TOKEN")).isNotNull();
        assertExactShape(result.getResponse().getContentAsString());
        assertRoleCount(result.getResponse().getContentAsString(), 0);
        assertNoSensitiveFields(result.getResponse().getContentAsString());
    }

    @Test
    @Transactional
    void playerSessionContainsOnlyPlayerContract() throws Exception {
        var player = players.saveAndFlush(PlayerPadel.builder()
                .nivelJugador(Nivel.SIN_ESPECIFICAR)
                .nombre("Ana")
                .apellido("Ruiz")
                .email("ana-" + UUID.randomUUID() + "@example.test")
                .passwordHash("must-not-leak")
                .telefono("+5491112345678")
                .telegramUsername("qa_session_" + UUID.randomUUID().toString().substring(0,8))
                .preferredLocale("ru")
                .activo(true)
                .emailConfirmado(true)
                .build());

        var result = mockMvc.perform(get("/api/auth/session").with(playerUser(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userType").value("PLAYER"))
                .andExpect(jsonPath("$.id").value(player.getId()))
                .andExpect(jsonPath("$.fullName").value("Ana Ruiz"))
                .andExpect(jsonPath("$.email").value(player.getEmail()))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_PLAYER"))
                .andExpect(jsonPath("$.clubId").value(nullValue()))
                .andExpect(jsonPath("$.hasValidContact").value(true))
                .andExpect(jsonPath("$.preferredLocale").value("ru"))
                .andReturn();

        assertExactShape(result.getResponse().getContentAsString());
        assertRoleCount(result.getResponse().getContentAsString(), 1);
        assertNoSensitiveFields(result.getResponse().getContentAsString());
    }

    @Test
    void clubAdminSessionContainsActualRoleAndClub() throws Exception {
        var owner = Owner.builder()
                .id(52L)
                .firstName("Club")
                .lastName("Admin")
                .email("club@example.com")
                .password("must-not-leak")
                .phone("must-not-leak")
                .role(OwnerRole.CLUB_ADMIN)
                .clubId(73L)
                .isActive(true)
                .build();

        var result = mockMvc.perform(get("/api/auth/session").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userType").value("OWNER"))
                .andExpect(jsonPath("$.id").value(52))
                .andExpect(jsonPath("$.fullName").value("Club Admin"))
                .andExpect(jsonPath("$.email").value("club@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_CLUB_ADMIN"))
                .andExpect(jsonPath("$.clubId").value(73))
                .andExpect(jsonPath("$.hasValidContact").value(nullValue()))
                .andExpect(jsonPath("$.preferredLocale").value(nullValue()))
                .andReturn();

        assertExactShape(result.getResponse().getContentAsString());
        assertRoleCount(result.getResponse().getContentAsString(), 1);
        assertNoSensitiveFields(result.getResponse().getContentAsString());
    }

    @Test
    void logoutRequiresCsrfAndAcceptsCookieHeaderPair() throws Exception {
        mockMvc.perform(post("/logout").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/logout"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/error/403"));

        var session = mockMvc.perform(get("/api/auth/session"))
                .andReturn();
        Cookie csrfCookie = Objects.requireNonNull(session.getResponse().getCookie("XSRF-TOKEN"));

        mockMvc.perform(post("/logout")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @Transactional
    void rememberMePlayerUsesSameExactSessionContract() throws Exception {
        var player = players.saveAndFlush(PlayerPadel.builder()
                .nivelJugador(Nivel.SIN_ESPECIFICAR)
                .passwordHash("must-not-leak")
                .nombre("Marta")
                .apellido("López")
                .email("marta-" + UUID.randomUUID() + "@example.test")
                .preferredLocale("es")
                .telefono("+5491112345678")
                .activo(true)
                .emailConfirmado(true)
                .build());
        var principal = principal(player);
        var rememberMe = new RememberMeAuthenticationToken("test-key", principal, principal.getAuthorities());

        var result = mockMvc.perform(get("/api/auth/session").with(authentication(rememberMe)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userType").value("PLAYER"))
                .andExpect(jsonPath("$.id").value(player.getId()))
                .andExpect(jsonPath("$.fullName").value("Marta López"))
                .andExpect(jsonPath("$.email").value(player.getEmail()))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_PLAYER"))
                .andExpect(jsonPath("$.clubId").value(nullValue()))
                .andExpect(jsonPath("$.hasValidContact").value(true))
                .andExpect(jsonPath("$.preferredLocale").value("es"))
                .andReturn();

        assertExactShape(result.getResponse().getContentAsString());
        assertRoleCount(result.getResponse().getContentAsString(), 1);
    }

    @Test
    void unsupportedPrincipalUsesSafeProblemDetail() throws Exception {
        var unsupported = User.withUsername("unsupported@example.com")
                .password("unused")
                .roles("PLAYER")
                .build();

        var result = mockMvc.perform(get("/api/auth/session").with(user(unsupported)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/problems/unsupported-principal"))
                .andExpect(jsonPath("$.title").value("Unsupported authenticated principal"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").value("/api/auth/session"))
                .andExpect(jsonPath("$.code").value("SPA_AUTH_UNSUPPORTED_PRINCIPAL"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("UnsupportedSpaPrincipalException", "stackTrace", "must-not-leak");
    }

    private static void assertNoSensitiveFields(String json) {
        assertThat(json).doesNotContain(
                "password", "telefono", "telegram", "accessToken", "refreshToken",
                "remember", "JSESSIONID", "XSRF-TOKEN"
        );
    }

    private void assertExactShape(String json) {
        assertThat(objectMapper.readTree(json).size()).isEqualTo(9);
    }

    private void assertRoleCount(String json, int expected) {
        assertThat(objectMapper.readTree(json).get("roles").size()).isEqualTo(expected);
    }
}
