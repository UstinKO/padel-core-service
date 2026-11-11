package com.padle.core.padelcoreservice.marketing;

import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.service.EmailService;
import com.padle.core.padelcoreservice.service.TournamentNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "browser.qa", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:postgresql://${BROWSER_QA_DB_HOST:localhost}:5432/padel_browser_qa",
        "spring.datasource.username=epadel_user", "spring.datasource.password=test",
        "app.analytics.meta.enabled=false", "app.analytics.meta.access-token=",
        "app.analytics.ga4-id=", "app.analytics.clarity-id=",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false",
        "spring.mail.username=", "spring.mail.password=",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test"
})
class RegistrationSourceBrowserTest {
    @LocalServerPort int port;
    @Autowired OwnerRepository owners;
    @Autowired PlayerRepository players;
    @Autowired TournamentRepository tournaments;
    @Autowired ClubRepository clubs;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoBean EmailService email;
    @MockitoBean TournamentNotificationService notifications;

    record Fixture(String baseUrl, String adminEmail, String playerEmail, String password,
                   Long tournamentId, Long playerId) { }

    @Test
    void realBrowsersFollowCampaignRegistrationAndAdminReport() throws Exception {
        Fixture fixture = new TransactionTemplate(transactions).execute(status -> seed());
        assertThat(fixture).isNotNull();
        Path directory = Files.createTempDirectory("padel-browser-qa-");
        Path input = directory.resolve("fixture.json");
        Path output = Path.of("target/browser-qa");
        Files.createDirectories(output);
        Files.writeString(input, json.writeValueAsString(fixture));
        try {
            var process = new ProcessBuilder("node", "tests/browser/registration-sources.browser.mjs", input.toString())
                    .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            boolean completed = process.waitFor(240, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
            }
            assertThat(completed).as("Браузерная проверка завершилась вовремя").isTrue();
            assertThat(process.exitValue()).as(Files.readString(output.resolve("browser.log"))).isZero();
            verifySnapshot(fixture);
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(directory);
        }
    }

    private Fixture seed() {
        String suffix = UUID.randomUUID().toString();
        String password = "Browser-qa-" + suffix;
        var admin = owners.saveAndFlush(Owner.builder().email("qa-admin-" + suffix + "@example.test")
                .password(passwords.encode(password)).firstName("Browser").lastName("QA")
                .role(OwnerRole.SUPER_ADMIN).isActive(true).build());
        var player = players.saveAndFlush(PlayerPadel.builder().nombre("Browser").apellido("Player")
                .email("qa-player-" + suffix + "@example.test").passwordHash(passwords.encode(password))
                .activo(true).emailConfirmado(true).preferredLocale("es").nivelJugador(Nivel.C9)
                .telefono("+5491155550000").telegramUsername("browser_qa_player").build());
        var club = clubs.saveAndFlush(Club.builder().nombre("Browser QA Club").isActive(true).build());
        var event = tournaments.saveAndFlush(Tournament.builder().nombre("Browser QA Event").clubId(club.getId())
                .fechaInicio(LocalDate.now().plusDays(7)).horaInicio(LocalTime.NOON).tipo(TournamentType.AMERICANO)
                .modalidad(Modalidad.INDIVIDUAL).generoFormato(GenderFormat.MIXTO).categoriaNivel(Nivel.C9)
                .cupoMax(16).precio(BigDecimal.ZERO).moneda("ARS").estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("qa@example.test").build());
        return new Fixture("http://127.0.0.1:" + port, admin.getEmail(), player.getEmail(), password,
                event.getId(), player.getId());
    }

    private void verifySnapshot(Fixture fixture) {
        var snapshot = jdbc.queryForObject("""
                SELECT mr.acquisition_touch, mr.registration_touch FROM marketing_registrations_db mr
                JOIN tournament_registrations_db r ON r.id = mr.registration_id
                WHERE r.tournament_id = ? AND r.player_id = ?
                """, (rs, row) -> new Snapshot(rs.getString("acquisition_touch"), rs.getString("registration_touch")),
                fixture.tournamentId(), fixture.playerId());
        assertThat(snapshot).isNotNull();
        assertThat(snapshot.first()).contains("reby");
        assertThat(snapshot.last()).contains("instagram");
    }

    private record Snapshot(String first, String last) { }
}
