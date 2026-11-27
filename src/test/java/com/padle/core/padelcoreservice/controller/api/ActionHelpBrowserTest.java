package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.application.identity.PlayingSide;
import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.support.OwnerFixtures;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.padelcoreservice.service.EmailService;
import com.padle.core.padelcoreservice.service.TournamentNotificationService;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramApi;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "browser.qa", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "app.scheduling.enabled=false", "recaptcha.enabled=false",
        "spring.datasource.url=${DB_URL:jdbc:postgresql://${BROWSER_QA_DB_HOST:localhost}:5432/padel_browser_qa}",
        "spring.datasource.username=${DB_USERNAME:epadel_user}", "spring.datasource.password=${DB_PASSWORD:test}",
        "app.security.whitelisted-ips=127.0.0.1", "app.auth.google-enabled=false",
        "telegram.enabled=false", "telegram.players.enabled=true", "telegram.players.automatic-enabled=false",
        "telegram.players.daily-summary-enabled=false", "app.analytics.meta.enabled=false",
        "app.analytics.ga4-id=", "app.analytics.clarity-id=",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false", "spring.mail.username=", "spring.mail.password=",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test", "recaptcha.site-key=test", "recaptcha.secret-key=test"
})
class ActionHelpBrowserTest {
    @LocalServerPort int port;
    @Autowired OwnerFixtures owners;
    @Autowired PlayerRepository players;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRepository events;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @MockitoBean EmailService email;
    @MockitoBean TournamentNotificationService notifications;
    @MockitoBean PlayerTelegramApi telegram;
    @MockitoBean PlayerTelegramProperties telegramProperties;

    record Account(String email, String role, long event, List<List<Long>> pairs) {}
    record Fixture(String baseUrl, String password, long club, String date, List<Account> accounts, long individual) {}

    @Test void contextualHelpPreservesFormsAndSupportsTheDoublesWorkflow() throws Exception {
        when(telegram.available()).thenReturn(true);
        when(telegramProperties.enabled()).thenReturn(true);
        when(telegramProperties.pilotEnabled()).thenReturn(false);
        when(telegramProperties.botUsername()).thenReturn("OnePadelQaBot");
        var fixture = new TransactionTemplate(transactions).execute(status -> seed());
        assertThat(fixture).isNotNull();
        var input = Files.createTempFile("action-help-browser-", ".json");
        var output = Path.of("target/action-help-qa");
        Files.createDirectories(output);
        Files.writeString(input, json.writeValueAsString(fixture));
        try {
            var process = new ProcessBuilder("node", "tests/browser/action-help.browser.mjs", input.toString())
                    .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            boolean completed = process.waitFor(900, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).as("Браузерная проверка завершилась вовремя").isTrue();
            assertThat(process.exitValue()).as(Files.readString(output.resolve("browser.log"))).isZero();
            assertThat(events.findById(fixture.accounts().get(3).event()).orElseThrow().getEstado()).isEqualTo(TournamentStatus.FINALIZADO);
        } finally {
            Files.deleteIfExists(input);
        }
    }

    private Fixture seed() {
        String suffix = UUID.randomUUID().toString();
        String password = "Help-qa-" + suffix;
        var club = clubs.saveAndFlush(Club.builder().nombre("Black Padel QA " + suffix).isActive(true).build());
        var accounts = List.of(OwnerRole.SUPER_ADMIN, OwnerRole.ADMIN, OwnerRole.ORGANIZER, OwnerRole.CLUB_ADMIN).stream()
                .map(role -> {
                    var owner = owners.saveAndFlush(Owner.builder().email(role.name().toLowerCase() + suffix + "@example.test")
                            .password(passwords.encode(password)).firstName("Help QA").lastName(role.name())
                            .role(role).isActive(true).clubIds(new HashSet<>(Set.of(club.getId()))).build());
                    var event = event(club, owner, TournamentType.AMERICANO_TEAMS);
                    var pairs = IntStream.range(0, 8).mapToObj(index -> pair(event, index, suffix + role)).toList();
                    single(event, "Solo Alfa", suffix + role + "a");
                    single(event, "Solo Bravo", suffix + role + "b");
                    return new Account(owner.getEmail(), role.name(), event.getId(), pairs);
                }).toList();
        var manager = owners.findByEmail(accounts.get(3).email()).orElseThrow();
        long individual = event(club, manager, TournamentType.AMERICANO).getId();
        return new Fixture("http://127.0.0.1:" + port, password, club.getId(), LocalDate.now().plusDays(10).toString(), accounts, individual);
    }

    private Tournament event(Club club, Owner owner, TournamentType type) {
        return events.saveAndFlush(Tournament.builder().nombre("Help QA " + type).clubId(club.getId()).ownerId(owner.getId())
                .fechaInicio(LocalDate.now().plusDays(10)).horaInicio(LocalTime.of(14, 0)).timeZone("America/Argentina/Buenos_Aires")
                .generoFormato(GenderFormat.MASCULINO).categoriaNivel(Nivel.C7).tipo(type)
                .modalidad(type == TournamentType.AMERICANO_TEAMS ? Modalidad.DOBLES : Modalidad.INDIVIDUAL)
                .contactoOrganizador("+5491123456789").cupoMax(16).precio(new BigDecimal("1000"))
                .moneda("ARS").estado(TournamentStatus.REGISTRO_ABIERTO).isActive(true).build());
    }

    private List<Long> pair(Tournament event, int index, String suffix) {
        var first = player("Jugador " + index + " A", suffix + index + "a");
        var second = player("Jugador " + index + " B", suffix + index + "b");
        UUID booking = UUID.randomUUID();
        var a = registration(event, first, first, second, booking, index + 1);
        var b = registration(event, second, first, first, booking, index + 1);
        return List.of(a.getId(), b.getId());
    }

    private TournamentRegistration registration(Tournament event, PlayerPadel player, PlayerPadel main,
                                               PlayerPadel partner, UUID booking, int position) {
        return registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(player).partner(partner)
                .mainPlayerId(main.getId()).pairBookingId(booking).isDoubleRegistration(true).position(position)
                .status(RegistrationStatus.CONFIRMED).build());
    }

    private void single(Tournament event, String name, String suffix) {
        registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(player(name, suffix))
                .pairBookingId(UUID.randomUUID()).status(RegistrationStatus.SOLO_SEARCH).build());
    }

    private PlayerPadel player(String name, String suffix) {
        return players.saveAndFlush(PlayerPadel.builder().nombre(name).apellido("Ejemplo").email(suffix + "@example.test")
                .passwordHash("unused").activo(true).emailConfirmado(true).preferredLocale("es")
                .nivelJugador(Nivel.C7).playingSide(PlayingSide.EITHER).build());
    }
}
