package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.service.EmailService;
import com.padle.core.padelcoreservice.service.TournamentNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Ручная браузерная приёмка только отдельного локального стенда. */
@EnabledIfSystemProperty(named = "rr.help.preview", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "server.servlet.session.cookie.name=RR1512SESSION",
        "spring.mail.username=test@example.test", "spring.mail.password=test", "recaptcha.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=test", "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test", "app.scheduling.enabled=false", "telegram.enabled=false",
        "telegram.players.enabled=false", "telegram.players.automatic-enabled=false", "logging.loki.enabled=false",
        "app.analytics.meta.enabled=false", "app.analytics.ga4-id=", "app.analytics.clarity-id=", "app.auth.google-enabled=false",
        "app.security.whitelisted-ips=127.0.0.1", "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false"
})
class RoundRobinHelpPreviewTest {
    @LocalServerPort int port;
    private final RoundRobinHelpFixtures fixtures;
    private final PlatformTransactionManager transactions;
    private final ObjectMapper json;

    @Autowired
    RoundRobinHelpPreviewTest(RoundRobinHelpFixtures fixtures, PlatformTransactionManager transactions, ObjectMapper json) {
        this.fixtures = fixtures;
        this.transactions = transactions;
        this.json = json;
    }
    @MockitoBean EmailService email;
    @MockitoBean TournamentNotificationService notifications;

    record Account(String email, String role, long points, long sets) {}
    record Preview(String baseUrl, String password, List<Account> accounts) {}

    @Test
    void serveSyntheticTournamentsUntilLocalStopFile() throws Exception {
        String password = "RoundRobin-1512-QA!";
        var accounts = new TransactionTemplate(transactions).execute(status ->
                List.of(OwnerRole.SUPER_ADMIN, OwnerRole.ADMIN, OwnerRole.ORGANIZER, OwnerRole.CLUB_ADMIN).stream()
                        .map(role -> {
                            var owner = fixtures.owner(role, password);
                            return new Account(owner.getEmail(), role.name(), fixtures.event(owner).getId(), fixtures.event(owner).getId());
                        }).toList());
        Files.writeString(Path.of("/private/tmp/rr1512-preview.json"),
                json.writeValueAsString(new Preview("http://127.0.0.1:" + port, password, accounts)));
        while (!Files.exists(Path.of("/private/tmp/rr1512-preview.stop"))) {
            Thread.sleep(250);
        }
    }
}
