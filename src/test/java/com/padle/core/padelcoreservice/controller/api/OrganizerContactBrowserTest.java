package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.padelcoreservice.support.OwnerFixtures;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.persistence.clubs.Club;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "organizer.browser.qa", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "app.scheduling.enabled=false", "recaptcha.enabled=false",
        "app.security.whitelisted-ips=127.0.0.1", "app.auth.google-enabled=false",
        "telegram.enabled=false", "telegram.players.enabled=false", "app.analytics.meta.enabled=false",
        "app.analytics.ga4-id=", "app.analytics.clarity-id=",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false", "spring.mail.username=", "spring.mail.password="
})
class OrganizerContactBrowserTest {
    @LocalServerPort int port;
    @Autowired OwnerFixtures owners;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRepository tournaments;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @MockitoBean EmailService email;
    @MockitoBean TournamentNotificationService notifications;
    record Account(String email, String role, List<Long> events, long invalidEvent) {}
    record Fixture(String baseUrl, String password, long clubId, List<Account> accounts) {}

    @Test void formAndLinksWorkAcrossRolesFormatsLanguagesAndWidths() throws Exception {
        Fixture fixture = new TransactionTemplate(transactions).execute(status -> seed());
        assertThat(fixture).isNotNull();
        Path input = Files.createTempFile("organizer-contact-browser-", ".json");
        Path output = Path.of("target/organizer-contact-qa");
        Files.createDirectories(output);
        Files.writeString(input, json.writeValueAsString(fixture));
        try {
            var process = new ProcessBuilder("node", "tests/browser/organizer-contact.browser.mjs", input.toString())
                    .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            boolean completed = process.waitFor(600, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).as("Проверка контактов завершилась вовремя").isTrue();
            assertThat(process.exitValue()).as(Files.readString(output.resolve("browser.log"))).isZero();
        } finally {
            Files.deleteIfExists(input);
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var ownerIds = fixture.accounts().stream().map(account -> owners.findByEmail(account.email()).orElseThrow().getId()).toList();
                var events = tournaments.findAll().stream().filter(event -> ownerIds.contains(event.getOwnerId())).toList();
                tournaments.deleteAll(events);
                tournaments.flush();
                owners.deleteAllById(ownerIds);
                owners.flush();
                clubs.deleteById(fixture.clubId());
            });
        }
    }

    private Fixture seed() {
        String suffix = UUID.randomUUID().toString();
        String password = "Organizer-qa-" + suffix;
        var club = clubs.saveAndFlush(Club.builder().nombre("Organizer QA " + suffix).isActive(true).build());
        var accounts = List.of(OwnerRole.SUPER_ADMIN, OwnerRole.CLUB_ADMIN, OwnerRole.ORGANIZER, OwnerRole.ADMIN).stream()
                .map(role -> {
                    var owner = owners.saveAndFlush(Owner.builder().email(role.name().toLowerCase() + suffix + "@example.test")
                            .password(passwords.encode(password)).firstName("Organizer QA").lastName(role.name())
                            .role(role).isActive(true).clubIds(new HashSet<>(Set.of(club.getId()))).build());
                    var ids = List.of(TournamentType.values()).stream().map(type -> event(club, owner, type, "QA Organizer +54 9 11 2345-6789").getId()).toList();
                    long invalid = event(club, owner, TournamentType.AMERICANO, "QA contact needs correction").getId();
                    var roundRobin = event(club, owner, TournamentType.AMERICANO, "+5491123456789");
                    roundRobin.setModalidad(Modalidad.DOBLES);
                    tournaments.saveAndFlush(roundRobin);
                    var allIds = Stream.concat(ids.stream(), Stream.of(roundRobin.getId())).toList();
                    return new Account(owner.getEmail(), role.name(), allIds, invalid);
                }).toList();
        return new Fixture("http://127.0.0.1:" + port, password, club.getId(), accounts);
    }

    private Tournament event(Club club, Owner owner, TournamentType type, String raw) {
        return tournaments.saveAndFlush(Tournament.builder().nombre("Organizer QA " + type)
                .clubId(club.getId()).ownerId(owner.getId()).fechaInicio(LocalDate.now().plusDays(10)).horaInicio(LocalTime.NOON)
                .generoFormato(GenderFormat.MASCULINO).categoriaNivel(Nivel.C7).tipo(type)
                .modalidad(type == TournamentType.AMERICANO_TEAMS ? Modalidad.DOBLES : Modalidad.INDIVIDUAL)
                .contactoOrganizador(raw).cupoMax(16).precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO).isActive(true).build());
    }
}
