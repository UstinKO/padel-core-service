package com.padle.core.padelcoreservice.config.spa;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.preview.PilotEloRefresh;
import com.padle.core.padelcoreservice.preview.PreviewEloRanking;
import com.padle.core.padelcoreservice.rating.EloPilotAccess;
import com.padle.core.padelcoreservice.rating.EloPilotProperties;
import com.padle.core.padelcoreservice.rating.EloPilotSnapshot;
import com.padle.core.padelcoreservice.rating.PilotEloRanking;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.util.ServletRequestPathUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class PilotDashboardRoutingTest {
    @TempDir Path directory;
    private final PilotEloRefresh refresh = mock(PilotEloRefresh.class);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private SpaForwardHandlerMapping mapping(boolean enabled) throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var row = new PreviewEloRanking.Row(226L, "Selected", "C7", true, BigDecimal.valueOf(1540),
                1, 1, 1, null, List.of(), null, List.of());
        var snapshot = new EloPilotSnapshot(new PreviewEloRanking.Snapshot(LocalDate.of(2026, 11, 13),
                List.of(row)), 40, Map.of(327L, 226L));
        var path = directory.resolve("snapshot.json");
        Files.writeString(path, mapper.writeValueAsString(snapshot));
        var properties = new EloPilotProperties(enabled, Set.of(226L), path);
        var access = new EloPilotAccess(properties);
        var beans = new DefaultListableBeanFactory();
        if (enabled) {
            beans.registerSingleton("pilot", new PilotEloRanking(mapper, properties, access, Optional.of(refresh)));
        }
        return new SpaWebConfig(new ReleaseProperties(false, "", "")).spaForwardHandlerMapping(
                new SpaProperties(List.of("/players/dashboard")), new SpaPageService(null, null, null,
                        new StaticMessageSource(), new ByteArrayResource("<main>React</main>".getBytes()), "http://localhost"),
                beans.getBeanProvider(PilotEloRanking.class), access);
    }

    private void login(long id) {
        var player = new PlayerPadel();
        player.setId(id);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(player, null, player.getAuthorities()));
    }

    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        ServletRequestPathUtils.parseAndCache(request);
        return request;
    }

    @ParameterizedTest
    @ValueSource(longs = {226, 327})
    void selectedPlayerAndAliasReachServerCabinetWithoutCalculatingRating(long id) throws Exception {
        login(id);
        var mapping = mapping(true);
        assertThat(mapping.getHandlerInternal(request("GET", "/players/dashboard"))).isNull();
        assertThat(mapping.getHandlerInternal(request("HEAD", "/players/dashboard"))).isNull();
        verifyNoInteractions(refresh);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void outsidersKeepReactDespiteSpoofedPlayerParameters(boolean enabled) throws Exception {
        login(143);
        var request = request("GET", "/players/dashboard");
        request.addParameter("pilot", "true");
        request.addParameter("playerId", "226");
        request.addHeader("X-Player-ID", "226");
        request.setCookies(new jakarta.servlet.http.Cookie("playerId", "226"));
        var handler = (HttpRequestHandler) mapping(enabled).getHandlerInternal(request);
        assertThat(handler).isNotNull();
        var response = new MockHttpServletResponse();
        handler.handleRequest(request, response);
        assertThat(response.getForwardedUrl()).isNull();
        assertThat(response.getContentAsString()).isEqualTo("<main>React</main>");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("private, no-store");
        assertThat(response.getHeaders("Vary")).contains("Cookie", "Authorization");
        verifyNoInteractions(refresh);
    }

    @Test
    void disabledPilotReturnsSelectedPlayerToReact() throws Exception {
        login(226);
        assertThat(mapping(false).getHandlerInternal(request("GET", "/players/dashboard"))).isNotNull();
        verifyNoInteractions(refresh);
    }

    @Test
    void anonymousAndUntrustedPrincipalCannotSwitchDashboard() throws Exception {
        var mapping = mapping(true);
        assertThat(mapping.getHandlerInternal(request("GET", "/players/dashboard"))).isNotNull();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "226", null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PLAYER"))));
        assertThat(mapping.getHandlerInternal(request("GET", "/players/dashboard"))).isNotNull();
        verifyNoInteractions(refresh);
    }

    @Test
    void otherRoutesAndMethodsKeepExistingBehavior() throws Exception {
        login(226);
        var mapping = mapping(true);
        assertThat(mapping.getHandlerInternal(request("GET", "/preview/players/dashboard"))).isNotNull();
        assertThat(mapping.getHandlerInternal(request("GET", "/ranking"))).isNull();
        assertThat(mapping.getHandlerInternal(request("POST", "/players/dashboard"))).isNull();
        verifyNoInteractions(refresh);
    }
}
