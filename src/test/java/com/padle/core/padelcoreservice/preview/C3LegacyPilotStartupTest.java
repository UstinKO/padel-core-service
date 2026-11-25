package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.rating.pilot.enabled=true", "app.rating.pilot.auto-refresh=true",
        "app.rating.pilot.player-ids=1", "app.scheduling.enabled=false",
        "app.rating.pilot.snapshot=src/test/resources/elo-c3/legacy-snapshot.json",
        "app.rating.pilot.history=src/test/resources/elo-c3/legacy-history.json",
        "management.endpoint.health.probes.enabled=true", "management.health.mail.enabled=false",
        "app.analytics.meta.enabled=false", "app.analytics.meta.access-token=",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false", "spring.mail.username=", "spring.mail.password=",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test"
})
@AutoConfigureMockMvc
class C3LegacyPilotStartupTest {
    @Autowired MockMvc mvc;
    @Autowired PilotEloRefresh refresh;
    @MockitoBean EmailService email;

    @Test
    void serverStartsAndReportsReadyWithLegacyC5Snapshot() throws Exception {
        assertThat(refresh).isNotNull();
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
}
