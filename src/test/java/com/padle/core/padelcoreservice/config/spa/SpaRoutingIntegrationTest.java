package com.padle.core.padelcoreservice.config.spa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
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
        "recaptcha.secret-key=test-secret-key",
        "app.spa.routes[0]=/react-pilot/**"
})
class SpaRoutingIntegrationTest {

    private static final String SPA_INDEX = "/web/index.html";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void allowlistedGetHeadDeepLinkAndQueryAreForwarded() throws Exception {
        mockMvc.perform(get("/react-pilot").with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(SPA_INDEX));
        mockMvc.perform(get("/react-pilot/path/to/screen?tab=details").with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(SPA_INDEX));
        mockMvc.perform(head("/react-pilot/path").with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(SPA_INDEX));
    }

    @Test
    void neighboringReservedAndMutatingRequestsAreNotIntercepted() throws Exception {
        mockMvc.perform(get("/react-other").with(user("tester")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/unknown").with(user("tester")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/app/"))
                .andExpect(forwardedUrl("/app/index.html"));
        mockMvc.perform(post("/react-pilot/path").with(user("tester")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void previewRequiresSuperAdminForRootAndDeepLink() throws Exception {
        mockMvc.perform(get("/preview"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));

        for (var role : new String[]{"PLAYER", "ORGANIZER", "ADMIN", "CLUB_ADMIN"}) {
            var result = mockMvc.perform(get("/preview/path/to/screen").with(user("tester").roles(role)))
                    .andReturn();
            assertThat(result.getResponse().getForwardedUrl()).isNotEqualTo(SPA_INDEX);
            assertThat(result.getResponse().getContentAsString()).doesNotContain("spa-test-shell");
        }

        mockMvc.perform(get("/preview").with(user("tester").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(SPA_INDEX));
        mockMvc.perform(get("/preview/").with(user("tester").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl(SPA_INDEX));
        mockMvc.perform(get("/preview/path/to/screen").with(user("tester").roles("SUPER_ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("spa-test-shell")));
    }

    @Test
    void legalAndSystemPreviewHaveRealStatusAndNoindexMetadata() throws Exception {
        mockMvc.perform(get("/preview/privacidad").with(user("tester").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/privacidad")));
        mockMvc.perform(get("/preview/error/503").with(user("tester").roles("SUPER_ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("spa-test-shell")));
    }

    @Test
    void webAndExpoResourcesRemainIndependent() throws Exception {
        mockMvc.perform(get("/web/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("spa-test-shell")));
        mockMvc.perform(get("/app/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not("spa-test-shell")));
    }

    @Test
    void staticResourcesUseExpectedCachingAndMissingFileIsNotForwarded() throws Exception {
        mockMvc.perform(get("/web/index.html"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        org.hamcrest.Matchers.containsString("no-cache")));
        mockMvc.perform(get("/web/assets/app.test.js"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("max-age=" + Duration.ofDays(365).toSeconds()),
                                org.hamcrest.Matchers.containsString("immutable")
                        )));
        mockMvc.perform(get("/web/missing.js"))
                .andExpect(status().isNotFound());
    }
}
