package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.application.identity.PlayingSide;
import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.persistence.tournament.Tournament;
import com.padle.core.persistence.tournament.TournamentRegistration;
import com.padle.core.application.tournament.Modalidad;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.application.registration.RegistrationStatus;
import com.padle.core.application.tournament.TournamentStatus;
import com.padle.core.application.tournament.TournamentType;
import com.padle.core.persistence.tournament.TournamentRepository;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.persistence.tournament.TournamentRegistrationRepository;
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
class IndividualActionHelpBrowserTest {
    @LocalServerPort int port;
    private final OwnerFixtures owners;
    private final PlayerRepository players;
    private final ClubRepository clubs;
    private final TournamentRepository events;
    private final TournamentKingOfCourtRepository kings;
    private final TournamentRegistrationRepository registrations;
    private final PasswordEncoder passwords;
    private final PlatformTransactionManager transactions;
    private final ObjectMapper json;
    @MockitoBean EmailService email;
    @MockitoBean TournamentNotificationService notifications;
    @MockitoBean PlayerTelegramApi telegram;
    @MockitoBean PlayerTelegramProperties telegramProperties;

    @Autowired
    IndividualActionHelpBrowserTest(OwnerFixtures owners, PlayerRepository players, ClubRepository clubs, TournamentRepository events, TournamentKingOfCourtRepository kings, TournamentRegistrationRepository registrations, PasswordEncoder passwords, PlatformTransactionManager transactions, ObjectMapper json) {
        this.owners = owners;
        this.players = players;
        this.clubs = clubs;
        this.events = events;
        this.kings = kings;
        this.registrations = registrations;
        this.passwords = passwords;
        this.transactions = transactions;
        this.json = json;
    }

    record Event(long id, List<Long> registrations) {}
    record Account(String email, String role, Event king, Event full, Event flex) {}
    record Fixture(String baseUrl, String password, long club, String date, List<Account> accounts) {}

    @Test void helpPreservesDraftsThroughoutIndividualTournaments() throws Exception {
        when(telegram.available()).thenReturn(true);
        when(telegramProperties.enabled()).thenReturn(true);
        when(telegramProperties.pilotEnabled()).thenReturn(false);
        when(telegramProperties.botUsername()).thenReturn("OnePadelQaBot");
        var fixture = new TransactionTemplate(transactions).execute(status -> seed());
        assertThat(fixture).isNotNull();
        var input = Files.createTempFile("action-help-browser-", ".json");
        var output = Path.of("target/individual-action-help-qa");
        Files.createDirectories(output);
        Files.writeString(input, json.writeValueAsString(fixture));
        try {
            var process = new ProcessBuilder("node", "tests/browser/individual-action-help.browser.mjs", input.toString())
                    .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            boolean completed = process.waitFor(900, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).as("Браузерная проверка завершилась вовремя").isTrue();
            assertThat(process.exitValue()).as(Files.readString(output.resolve("browser.log"))).isZero();
            assertThat(kings.findFirstByTournamentIdOrderByIsActiveDescIdDesc(fixture.accounts().get(3).king().id())
                    .orElseThrow().getIsFinished()).isTrue();
            for (var event : List.of(fixture.accounts().get(3).full(), fixture.accounts().get(3).flex())) {
                assertThat(events.findById(event.id()).orElseThrow().getEstado()).isEqualTo(TournamentStatus.FINALIZADO);
            }
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
                    return new Account(owner.getEmail(), role.name(),
                            event(club, owner, TournamentType.KING_OF_COURT, 8, suffix + role + "k"),
                            event(club, owner, TournamentType.AMERICANO, 4, suffix + role + "a"),
                            event(club, owner, TournamentType.AMERICANO, 5, suffix + role + "f"));
                }).toList();
        return new Fixture("http://127.0.0.1:" + port, password, club.getId(), LocalDate.now().plusDays(10).toString(), accounts);
    }

    private Event event(Club club, Owner owner, TournamentType type, int count, String suffix) {
        var event = event(club, owner, type);
        var ids = IntStream.range(0, count).mapToObj(index -> registrations.saveAndFlush(TournamentRegistration.builder()
                .tournament(event).player(player("Jugador " + index, suffix + index))
                .status(RegistrationStatus.CONFIRMED).position(index + 1).build()).getId()).toList();
        registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(player("Reserva", suffix + "r"))
                .status(RegistrationStatus.WAITLIST).position(1).build());
        return new Event(event.getId(), ids);
    }

    private Tournament event(Club club, Owner owner, TournamentType type) {
        return events.saveAndFlush(Tournament.builder().nombre("Help QA " + type).clubId(club.getId()).ownerId(owner.getId())
                .fechaInicio(LocalDate.now().plusDays(10)).horaInicio(LocalTime.of(14, 0)).timeZone("America/Argentina/Buenos_Aires")
                .generoFormato(GenderFormat.MASCULINO).categoriaNivel(Nivel.C7).tipo(type)
                .modalidad(type == TournamentType.AMERICANO_TEAMS ? Modalidad.DOBLES : Modalidad.INDIVIDUAL)
                .contactoOrganizador("+5491123456789").cupoMax(16).precio(new BigDecimal("1000"))
                .moneda("ARS").estado(TournamentStatus.CERRADO).isActive(true).build());
    }

    private PlayerPadel player(String name, String suffix) {
        return players.saveAndFlush(PlayerPadel.builder().nombre(name).apellido("Ejemplo").email(suffix + "@example.test")
                .passwordHash("unused").activo(true).emailConfirmado(true).preferredLocale("es")
                .nivelJugador(Nivel.C7).playingSide(PlayingSide.EITHER).build());
    }
}
