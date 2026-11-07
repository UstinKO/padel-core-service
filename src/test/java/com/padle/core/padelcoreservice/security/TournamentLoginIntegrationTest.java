package com.padle.core.padelcoreservice.security;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.security.oauth2.CustomOAuth2User;
import com.padle.core.padelcoreservice.security.oauth2.CustomOAuth2UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.mail.username=test@example.com", "spring.mail.password=test",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test"
})
@AutoConfigureMockMvc
@Transactional
class TournamentLoginIntegrationTest {

    private static final String PASSWORD = "Browser-Test-Password";

    @Autowired MockMvc mvc;
    @Autowired PlayerRepository players;
    @Autowired PasswordEncoder passwords;
    @MockitoBean CustomOAuth2UserService googleUsers;
    @MockitoBean(name = "authorizationCodeTokenResponseClient")
    OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> googleTokens;

    @Test
    void passwordLoginReturnsToSelectedTournamentAfterRetry() throws Exception {
        var player = player();
        mvc.perform(get("/login").param("tournamentId", "42"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"tournamentId\" value=\"42\"")))
                .andExpect(content().string(containsString("/oauth2/authorization/google?tournamentId=42")));
        mvc.perform(post("/login").with(csrf())
                        .param("username", player.getEmail()).param("password", "wrong")
                        .param("tournamentId", "42"))
                .andExpect(redirectedUrl("/login?error=true&tournamentId=42"));
        mvc.perform(post("/login").with(csrf())
                        .param("username", player.getEmail()).param("password", PASSWORD)
                        .param("tournamentId", "42"))
                .andExpect(redirectedUrl("/torneo/42#tournament-registration"));
    }

    @Test
    void generalLoginAndUnsafeTargetsKeepDefaultRedirect() throws Exception {
        var player = player();
        mvc.perform(get("/login").param("tournamentId", "//example.test/42"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"tournamentId\""))));
        mvc.perform(post("/login").with(csrf())
                        .param("username", player.getEmail()).param("password", PASSWORD)
                        .param("tournamentId", "https://example.test/42"))
                .andExpect(redirectedUrl("/players/dashboard"));
    }

    @Test
    void googleCallbackUsesTournamentFromMatchingAuthorizationState() throws Exception {
        var player = player();
        OAuth2User providerUser = mock(OAuth2User.class);
        when(providerUser.getAttributes()).thenReturn(Map.of("sub", "test-provider-player"));
        when(googleUsers.loadUser(any())).thenReturn(new CustomOAuth2User(providerUser, player));
        when(googleTokens.getTokenResponse(any())).thenReturn(OAuth2AccessTokenResponse.withToken("test-token")
                .tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(60)
                .scopes(Set.of("email", "profile")).build());

        var flow = googleFlow("42");
        mvc.perform(get("/login/oauth2/code/google").session(flow.session())
                        .param("state", flow.state()).param("code", "test-code")
                        .param("tournamentId", "99"))
                .andExpect(redirectedUrl("/torneo/42#tournament-registration"));
    }

    @Test
    void googleCancellationPreservesOnlyTheMatchingTournament() throws Exception {
        var flow = googleFlow("42");
        mvc.perform(get("/login/oauth2/code/google").session(flow.session())
                        .param("state", "invalid-state").param("error", "access_denied")
                        .param("tournamentId", "99"))
                .andExpect(redirectedUrl("/login?error=oauth2"));
        mvc.perform(get("/login/oauth2/code/google").session(flow.session())
                        .param("state", flow.state()).param("error", "access_denied"))
                .andExpect(redirectedUrl("/login?error=oauth2&tournamentId=42"));
    }

    private GoogleFlow googleFlow(String tournamentId) throws Exception {
        var result = mvc.perform(get("/oauth2/authorization/google").param("tournamentId", tournamentId))
                .andExpect(status().is3xxRedirection()).andReturn();
        var location = result.getResponse().getRedirectedUrl();
        assertThat(location).startsWith("https://accounts.google.com/").doesNotContain("tournamentId");
        var state = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
        assertThat(state).isNotBlank();
        return new GoogleFlow((MockHttpSession) result.getRequest().getSession(false),
                URLDecoder.decode(state, StandardCharsets.UTF_8));
    }

    private PlayerPadel player() {
        return players.saveAndFlush(PlayerPadel.builder().nombre("Login").apellido("Test")
                .email(UUID.randomUUID() + "@example.test").passwordHash(passwords.encode(PASSWORD))
                .activo(true).emailConfirmado(true).preferredLocale("es").nivelJugador(Nivel.C9).build());
    }

    private record GoogleFlow(MockHttpSession session, String state) { }
}
