package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.service.EmailService;
import com.padle.core.padelcoreservice.service.TournamentNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "browser.qa", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "app.scheduling.enabled=false", "recaptcha.enabled=false",
        "app.security.whitelisted-ips=127.0.0.1", "app.auth.google-enabled=false",
        "spring.datasource.url=${GUEST_MERGE_QA_DB_URL:jdbc:postgresql://${BROWSER_QA_DB_HOST:localhost}:5432/padel_browser_qa}",
        "spring.datasource.username=${GUEST_MERGE_QA_DB_USER:epadel_user}",
        "spring.datasource.password=${GUEST_MERGE_QA_DB_PASSWORD:test}",
        "telegram.enabled=false", "telegram.players.enabled=false", "app.analytics.meta.enabled=false",
        "app.analytics.ga4-id=", "app.analytics.clarity-id=",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false", "spring.mail.username=", "spring.mail.password="
})
class GuestProfileMergeBrowserTest {
    @LocalServerPort int port;
    @Autowired OwnerRepository owners;
    @Autowired PlayerRepository players;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRepository tournaments;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @MockitoBean EmailService email;
    @MockitoBean TournamentNotificationService notifications;

    record Scenario(String language, boolean mobile, long guestId, long accountId, long registrationId, String accountEmail) {}
    record Fixture(String baseUrl, String adminEmail, String password, List<Scenario> scenarios,
                   long conflictGuestId, long conflictAccountId, long conflictEventId) {}

    @Test void manualMergeHasExplicitOutcomeAndPreservesAccountOnDesktopAndPhone() throws Exception {
        Fixture fixture = new TransactionTemplate(transactions).execute(status -> seed());
        assertThat(fixture).isNotNull();
        Path directory = Files.createTempDirectory("padel-merge-browser-");
        Path input = directory.resolve("fixture.json");
        Path output = Path.of("target/guest-merge-browser-qa");
        Files.createDirectories(output);
        Files.writeString(input, json.writeValueAsString(fixture));
        try {
            var process = new ProcessBuilder("node", "tests/browser/guest-profile-merge.browser.mjs", input.toString())
                    .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            boolean completed = process.waitFor(240, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).as("Проверка объединения в браузере завершилась вовремя").isTrue();
            assertThat(process.exitValue()).as(Files.readString(output.resolve("browser.log"))).isZero();
            for (var scenario : fixture.scenarios()) {
                var account = players.findById(scenario.accountId()).orElseThrow();
                assertThat(account.getTelefono()).isNull();
                assertThat(account.getTelegramUsername()).startsWith("@QaMerge");
                assertThat(account.getNivelJugador()).isEqualTo(Nivel.C7);
                assertThat(passwords.matches(fixture.password(), account.getPasswordHash())).isTrue();
                assertThat(registrations.findById(scenario.registrationId()).orElseThrow().getPlayer().getId())
                        .isEqualTo(scenario.accountId());
                assertThat(players.findById(scenario.guestId()).orElseThrow().isActivo()).isFalse();
            }
            assertThat(players.findById(fixture.conflictGuestId()).orElseThrow().isActivo()).isTrue();
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(directory);
        }
    }

    private Fixture seed() {
        String suffix = UUID.randomUUID().toString();
        String password = "Merge-qa-" + suffix;
        var admin = owners.saveAndFlush(Owner.builder().email("merge-admin-" + suffix + "@example.test")
                .password(passwords.encode(password)).firstName("Merge").lastName("QA")
                .role(OwnerRole.SUPER_ADMIN).isActive(true).build());
        var club = clubs.saveAndFlush(Club.builder().nombre("Merge Browser QA " + suffix).isActive(true).build());
        var languages = List.of("ru", "es", "en");
        var scenarios = IntStream.range(0, 6).mapToObj(index -> {
            var guest = player(true, index, suffix, password);
            var account = player(false, index, suffix, password);
            var event = event(club, admin);
            var row = registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(guest)
                    .status(RegistrationStatus.CONFIRMED).position(1).build());
            return new Scenario(languages.get(index / 2), index % 2 == 1, guest.getId(), account.getId(), row.getId(), account.getEmail());
        }).toList();
        var guest = player(true, 99, suffix, password);
        var account = player(false, 99, suffix, password);
        var event = event(club, admin);
        registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(guest)
                .status(RegistrationStatus.CONFIRMED).position(1).build());
        registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(account)
                .status(RegistrationStatus.CONFIRMED).position(2).build());
        return new Fixture("http://127.0.0.1:" + port, admin.getEmail(), password, scenarios,
                guest.getId(), account.getId(), event.getId());
    }

    private PlayerPadel player(boolean guest, int index, String suffix, String password) {
        String handle = "@QaMerge" + suffix.substring(0, 8) + index;
        return players.saveAndFlush(PlayerPadel.builder().nombre("Merge QA").apellido((guest ? "Guest " : "Main ") + index)
                .email((guest ? "guest" : "main") + index + "-" + suffix + (guest ? "@1padel.guest" : "@example.test"))
                .telefono(guest ? handle : null).telegramUsername(guest ? null : handle)
                .passwordHash(passwords.encode(password)).activo(true).emailConfirmado(true).preferredLocale("es")
                .nivelJugador(guest ? Nivel.C6 : Nivel.C7).playingSide(guest ? PlayingSide.LEFT : PlayingSide.RIGHT).build());
    }

    private Tournament event(Club club, Owner admin) {
        return tournaments.saveAndFlush(Tournament.builder().nombre("Merge QA " + UUID.randomUUID())
                .clubId(club.getId()).ownerId(admin.getId()).fechaInicio(LocalDate.now().plusDays(7)).horaInicio(LocalTime.NOON)
                .tipo(TournamentType.AMERICANO).modalidad(Modalidad.INDIVIDUAL).generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7).cupoMax(8).precio(BigDecimal.ZERO).moneda("ARS")
                .estado(TournamentStatus.REGISTRO_ABIERTO).contactoOrganizador("qa@example.test").build());
    }
}
