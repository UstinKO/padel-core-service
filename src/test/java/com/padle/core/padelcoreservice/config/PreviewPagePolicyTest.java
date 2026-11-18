package com.padle.core.padelcoreservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PreviewPagePolicyTest {
    private boolean allowed(String profile, String method, String path) {
        var environment = new MockEnvironment().withProperty("spring.profiles.active", profile);
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return PreviewPagePolicy.allowsPublicRead(request, environment);
    }

    @Test
    void publicNavigationCanBeReloadedOnlyInsidePreview() {
        for (var path : List.of("/preview", "/preview/", "/preview/torneos", "/preview/clubs",
                "/preview/clubs/14", "/preview/torneo/180", "/preview/login",
                "/preview/players/registro", "/preview/recuperar-password/solicitar",
                "/preview/recuperar-password", "/preview/terminos", "/preview/privacidad", "/preview/cookies")) {
            for (var method : List.of("GET", "HEAD")) {
                assertThat(allowed("preview", method, path)).as("%s %s", method, path).isTrue();
                assertThat(allowed("production", method, path)).isFalse();
                assertThat(allowed("test", method, path)).isFalse();
            }
            assertThat(allowed("preview", "POST", path)).isFalse();
            assertThat(allowed("preview", "DELETE", path)).isFalse();
        }
    }

    @Test
    void neighboringPersonalAndAdministrativePagesStayProtected() {
        for (var path : List.of("/preview/admin", "/preview/components", "/preview/foundation",
                "/preview/players/dashboard", "/preview/players/events", "/preview/perfil", "/preview/unknown",
                "/preview/torneo/0", "/preview/torneo/14/admin", "/preview/clubs/14/edit",
                "/preview/clubs/14/", "/admin", "/api/internal/ui-preview")) {
            assertThat(allowed("preview", "GET", path)).as(path).isFalse();
        }
    }
}
