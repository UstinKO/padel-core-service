package com.padle.core.padelcoreservice.config.spa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        "app.spa.routes="
})
class SpaRoutingDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void currentProductionRoutesAreNotForwardedToSpa() throws Exception {
        var root = mockMvc.perform(get("/"))
                .andReturn();
        var tournaments = mockMvc.perform(get("/torneos"))
                .andReturn();

        assertThat(root.getResponse().getForwardedUrl()).isNotEqualTo("/web/index.html");
        assertThat(tournaments.getResponse().getForwardedUrl()).isNotEqualTo("/web/index.html");
    }

    @Test
    void unknownAuthenticatedRouteRemainsNotFound() throws Exception {
        mockMvc.perform(get("/route-that-does-not-exist").with(user("tester")))
                .andExpect(status().isNotFound());
    }
}
