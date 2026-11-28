package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.application.identity.PlayingSide;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.persistence.tournament.TournamentRegistrationRepository;
import com.padle.core.persistence.tournament.TournamentRepository;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@EnabledIfSystemProperty(named = "browser.qa", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "server.servlet.session.cookie.name=ADMINHELPSESSION",
        "spring.datasource.url=${DB_URL:jdbc:postgresql://${BROWSER_QA_DB_HOST:localhost}:5432/padel_browser_qa}",
        "spring.datasource.username=${DB_USERNAME:epadel_user}", "spring.datasource.password=${DB_PASSWORD:test}",
        "spring.mail.username=test@example.test", "spring.mail.password=test", "recaptcha.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=test", "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test", "app.scheduling.enabled=false", "telegram.enabled=false",
        "telegram.players.enabled=false", "telegram.players.automatic-enabled=false", "logging.loki.enabled=false",
        "app.analytics.meta.enabled=false", "app.analytics.ga4-id=", "app.analytics.clarity-id=", "app.auth.google-enabled=false",
        "app.security.whitelisted-ips=127.0.0.1", "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false"
})
class AdminSectionHelpBrowserTest {
    @LocalServerPort int port;
    @Autowired RoundRobinHelpFixtures fixtures;
    @Autowired PlayerRepository players;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired TournamentRepository events;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @MockitoBean EmailService email;
    @MockitoBean TournamentNotificationService notifications;

    record Account(String email, String role, long event, long player) {}
    record Fixture(String baseUrl, String password, List<Account> accounts, long guest, long main, String date) {}

    @Test
    void helpPreservesCalendarAndProfileDraftsWithoutWrites() throws Exception {
        String password = "Admin-help-QA-only!";
        var fixture = new TransactionTemplate(transactions).execute(status -> {
            var accounts = List.of(OwnerRole.SUPER_ADMIN, OwnerRole.ADMIN, OwnerRole.ORGANIZER, OwnerRole.CLUB_ADMIN).stream()
                    .map(role -> {
                        var owner = fixtures.owner(role, password);
                        var event = fixtures.event(owner);
                        long player = registrations.findByTournamentId(event.getId()).get(0).getPlayer().getId();
                        return new Account(owner.getEmail(), role.name(), event.getId(), player);
                    }).toList();
            var contact = "@QaHelp" + UUID.randomUUID().toString().substring(0, 8);
            return new Fixture("http://127.0.0.1:" + port, password, accounts, profile(true, contact).getId(), profile(false, contact).getId(),
                    events.findById(accounts.get(0).event()).orElseThrow().getFechaInicio().toString());
        });
        assertThat(fixture).isNotNull();
        long before = players.count();
        var input = Files.createTempFile("admin-help-browser-", ".json");
        Files.writeString(input, json.writeValueAsString(fixture));
        try {
            if (Boolean.getBoolean("admin.help.preview")) {
                Files.writeString(Path.of("/private/tmp/admin1515-preview.json"), json.writeValueAsString(fixture));
                while (!Files.exists(Path.of("/private/tmp/admin1515-preview.stop"))) Thread.sleep(250);
            } else {
                var output = Path.of("target/admin-section-help-qa");
                Files.createDirectories(output);
                var process = new ProcessBuilder("node", "tests/browser/admin-section-help.browser.mjs", input.toString())
                        .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
                boolean completed = process.waitFor(600, TimeUnit.SECONDS);
                if (!completed) process.destroyForcibly();
                assertThat(completed).as("Браузерная проверка завершилась вовремя").isTrue();
                assertThat(process.exitValue()).as(Files.readString(output.resolve("browser.log"))).isZero();
            }
            assertThat(players.count()).isEqualTo(before);
            for (long id : new long[]{fixture.guest(), fixture.main()}) {
                var player = players.findById(id).orElseThrow();
                assertThat(player.getPasswordHash()).isEqualTo("unused-qa-profile");
                assertThat(player.getNivelJugador()).isEqualTo(Nivel.C7);
                assertThat(player.getPlayingSide()).isEqualTo(id == fixture.guest() ? PlayingSide.LEFT : PlayingSide.RIGHT);
                assertThat(player.isActivo()).isTrue();
            }
            verifyNoInteractions(email, notifications);
        } finally {
            Files.deleteIfExists(input);
        }
    }

    private PlayerPadel profile(boolean guest, String contact) {
        return players.saveAndFlush(PlayerPadel.builder().nombre("Help QA").apellido(guest ? "Guest" : "Main")
                .email("help-" + UUID.randomUUID() + (guest ? "@1padel.guest" : "@example.test"))
                .telefono(guest ? contact : null).telegramUsername(guest ? null : contact)
                .passwordHash("unused-qa-profile").activo(true).emailConfirmado(true).preferredLocale("es")
                .nivelJugador(Nivel.C7).playingSide(guest ? PlayingSide.LEFT : PlayingSide.RIGHT).build());
    }
}
