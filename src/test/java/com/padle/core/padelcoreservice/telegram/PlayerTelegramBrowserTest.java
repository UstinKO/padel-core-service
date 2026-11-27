package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.model.*;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.persistence.clubs.ClubRepository;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "browser.qa", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "app.scheduling.enabled=false",
        "spring.datasource.url=jdbc:postgresql://${BROWSER_QA_DB_HOST:localhost}:5432/padel_browser_qa",
        "spring.datasource.username=epadel_user", "spring.datasource.password=test",
        "telegram.enabled=false", "telegram.players.enabled=true",
        "telegram.players.bot-username=OnePadelQaBot", "telegram.players.automatic-enabled=false",
        "telegram.players.daily-summary-enabled=false",
        "app.analytics.meta.enabled=false", "app.analytics.meta.access-token=",
        "app.analytics.ga4-id=", "app.analytics.clarity-id=",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false",
        "spring.mail.username=", "spring.mail.password=",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test"
})
class PlayerTelegramBrowserTest {
    @LocalServerPort int port;
    @Autowired OwnerRepository owners;
    @Autowired PlayerRepository players;
    @Autowired TournamentRepository tournaments;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired ClubRepository clubs;
    @Autowired PlayerTelegramStore store;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @MockitoBean PlayerTelegramApi api;
    @MockitoBean PlayerTelegramProperties properties;
    @MockitoBean EmailService email;
    @MockitoBean TournamentNotificationService notifications;

    @BeforeEach void configure() {
        when(api.available()).thenReturn(true);
        when(properties.enabled()).thenReturn(true);
        when(properties.pilotEnabled()).thenReturn(false);
        when(properties.botUsername()).thenReturn("OnePadelQaBot");
    }

    record Scenario(String language, String email, long playerId, long eventId, long reserveId) {}
    record Fixture(String baseUrl, String adminEmail, String password, List<Scenario> scenarios,
                   String connectedEmail, long connectedEventId) {}

    record DisclosureAccount(String email, String role, long ownerId, List<Long> eventIds) {}
    record DisclosureFixture(String baseUrl, String password, List<DisclosureAccount> accounts) {}

    @Test void paymentDisclosurePreservesDraftsAcrossRolesAndRequests() throws Exception {
        var fixture = new TransactionTemplate(transactions).execute(status -> seedDisclosure());
        assertThat(fixture).isNotNull();
        Path directory = Files.createTempDirectory("padel-telegram-disclosure-");
        Path input = directory.resolve("fixture.json");
        Path output = Path.of("target/telegram-browser-qa");
        Files.createDirectories(output);
        Files.writeString(input, json.writeValueAsString(fixture));
        try {
            var process = new ProcessBuilder("node", "tests/browser/telegram-disclosure.browser.mjs", input.toString())
                    .redirectErrorStream(true).redirectOutput(output.resolve("disclosure-browser.log").toFile()).start();
            boolean completed = process.waitFor(420, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).as("Проверка сворачивания блока оплат завершилась вовремя").isTrue();
            assertThat(process.exitValue()).as(Files.readString(output.resolve("disclosure-browser.log"))).isZero();
            verify(api, never()).send(anyLong(), anyString(), anyList());
            verify(api, never()).updates(anyLong());
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(directory);
        }
    }

    private DisclosureFixture seedDisclosure() {
        String suffix = UUID.randomUUID().toString();
        String password = "Disclosure-qa-" + suffix;
        var club = clubs.saveAndFlush(Club.builder().nombre("Disclosure QA " + suffix).isActive(true).build());
        var roster = IntStream.range(0, 20).mapToObj(i -> player("collapse-" + i, suffix, password, 500 + i)).toList();
        var accounts = List.of(OwnerRole.SUPER_ADMIN, OwnerRole.ADMIN, OwnerRole.ORGANIZER, OwnerRole.CLUB_ADMIN)
                .stream().map(role -> {
                    var owner = owners.saveAndFlush(Owner.builder().email("collapse-" + role + "-" + suffix + "@example.test")
                            .password(passwords.encode(password)).firstName("Disclosure").lastName(role.name())
                            .role(role).clubId(club.getId()).isActive(true).build());
                    var eventIds = IntStream.range(0, 2).mapToObj(i -> {
                        var tournament = event(club, owner, 32);
                        roster.forEach(player -> registrations.saveAndFlush(TournamentRegistration.builder()
                                .tournament(tournament).player(player).status(RegistrationStatus.CONFIRMED).build()));
                        return tournament.getId();
                    }).toList();
                    return new DisclosureAccount(owner.getEmail(), role.name(), owner.getId(), eventIds);
                }).toList();
        return new DisclosureFixture("http://127.0.0.1:" + port, password, accounts);
    }

    @Test void browsersPersistPreferencesAcrossDevicesAndHonorSiteLanguage() throws Exception {
        when(api.available()).thenReturn(true);
        Fixture fixture = new TransactionTemplate(transactions).execute(status -> seed());
        assertThat(fixture).isNotNull();
        Path directory = Files.createTempDirectory("padel-telegram-browser-");
        Path input = directory.resolve("fixture.json");
        Path output = Path.of("target/telegram-browser-qa");
        Files.createDirectories(output);
        Files.writeString(input, json.writeValueAsString(fixture));
        try {
            var process = new ProcessBuilder("node", "tests/browser/player-telegram.browser.mjs", input.toString())
                    .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            boolean completed = process.waitFor(240, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).as("Браузерная проверка Telegram завершилась вовремя").isTrue();
            assertThat(process.exitValue()).as(Files.readString(output.resolve("browser.log"))).isZero();
            fixture.scenarios().forEach(scenario -> {
                assertThat(store.preference(scenario.playerId())).isEqualTo(TelegramModels.ContactPreference.TELEGRAM);
                assertThat(players.findById(scenario.playerId()).orElseThrow().getPreferredLocale()).isEqualTo(scenario.language());
                assertThat(registrations.findByTournamentIdAndPlayerId(scenario.eventId(), scenario.playerId()).orElseThrow().getStatus())
                        .isEqualTo(RegistrationStatus.CONFIRMED);
                assertThat(registrations.findByTournamentIdAndPlayerId(scenario.reserveId(), scenario.playerId()).orElseThrow().getStatus())
                        .isEqualTo(RegistrationStatus.WAITLIST);
            });
            verify(api, never()).send(anyLong(), anyString(), anyList());
            verify(api, never()).updates(anyLong());
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(directory);
        }
    }

    @Test void pilotIsVisibleOnlyToTheSelectedAccountAndEvent() throws Exception {
        Fixture fixture = new TransactionTemplate(transactions).execute(status -> seed());
        assertThat(fixture).isNotNull();
        var selected = fixture.scenarios().get(0);
        when(properties.pilotEnabled()).thenReturn(true);
        when(properties.pilotPlayerId()).thenReturn(selected.playerId());
        when(properties.pilotTournamentId()).thenReturn(selected.eventId());
        Path directory = Files.createTempDirectory("padel-telegram-pilot-browser-");
        Path input = directory.resolve("fixture.json");
        Path output = Path.of("target/telegram-browser-qa");
        Files.createDirectories(output);
        Files.writeString(input, json.writeValueAsString(fixture));
        try {
            var process = new ProcessBuilder("node", "tests/browser/player-telegram-pilot.browser.mjs", input.toString())
                    .redirectErrorStream(true).redirectOutput(output.resolve("pilot-browser.log").toFile()).start();
            boolean completed = process.waitFor(120, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).as("Проверка ограниченного пилота завершилась вовремя").isTrue();
            assertThat(process.exitValue()).as(Files.readString(output.resolve("pilot-browser.log"))).isZero();
            assertThat(store.preference(selected.playerId())).isEqualTo(TelegramModels.ContactPreference.TELEGRAM);
            assertThat(store.preference(fixture.scenarios().get(1).playerId())).isEqualTo(TelegramModels.ContactPreference.UNSPECIFIED);
            verify(api, never()).send(anyLong(), anyString(), anyList());
            verify(api, never()).updates(anyLong());
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(directory);
        }
    }

    private Fixture seed() {
        String suffix = UUID.randomUUID().toString();
        String password = "Telegram-qa-" + suffix;
        var admin = owners.saveAndFlush(Owner.builder().email("tg-admin-" + suffix + "@example.test")
                .password(passwords.encode(password)).firstName("Telegram").lastName("QA")
                .role(OwnerRole.SUPER_ADMIN).isActive(true).build());
        var club = clubs.saveAndFlush(Club.builder().nombre("Telegram Browser QA " + suffix).isActive(true).build());
        var connected = player("connected", suffix, password, 299);
        store.token(connected.getId(), "browser-verified-token", OffsetDateTime.now().plusMinutes(10), "es");
        long chatId = 7_770_000_000L + Integer.toUnsignedLong(suffix.hashCode());
        store.bind("browser-verified-token", chatId, chatId).orElseThrow();
        var languages = List.of("es", "ru", "en");
        var scenarios = IntStream.range(0, languages.size()).mapToObj(index -> {
            var player = player(languages.get(index), suffix, password, 201 + index);
            var event = event(club, admin, 16);
            var reserve = event(club, admin, 1);
            registrations.saveAndFlush(TournamentRegistration.builder().tournament(reserve).player(connected)
                    .status(RegistrationStatus.CONFIRMED).position(1).build());
            return new Scenario(languages.get(index), player.getEmail(), player.getId(), event.getId(), reserve.getId());
        }).toList();
        return new Fixture("http://127.0.0.1:" + port, admin.getEmail(), password, scenarios,
                connected.getEmail(), event(club, admin, 16).getId());
    }

    private PlayerPadel player(String name, String suffix, String password, int phoneSuffix) {
        return players.saveAndFlush(PlayerPadel.builder().nombre("Telegram").apellido(name)
                .email("tg-" + name + "-" + suffix + "@example.test").passwordHash(passwords.encode(password))
                .activo(true).emailConfirmado(true).preferredLocale("es").nivelJugador(Nivel.C9)
                .telefono("+54911" + (10000000 + Math.floorMod(suffix.hashCode(), 8000000) + phoneSuffix)).build());
    }

    private Tournament event(Club club, Owner admin, int capacity) {
        return tournaments.saveAndFlush(Tournament.builder().nombre("Telegram QA " + UUID.randomUUID())
                .clubId(club.getId()).ownerId(admin.getId()).fechaInicio(LocalDate.now().plusDays(7)).horaInicio(LocalTime.NOON)
                .tipo(TournamentType.AMERICANO).modalidad(Modalidad.INDIVIDUAL).generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C9).cupoMax(capacity).precio(BigDecimal.ZERO).moneda("ARS")
                .estado(TournamentStatus.REGISTRO_ABIERTO).contactoOrganizador("qa@example.test").build());
    }
}
