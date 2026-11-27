package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.application.identity.PlayingSide;
import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.support.OwnerFixtures;
import com.padle.core.padelcoreservice.dto.americano.AmericanoConfigDto;
import com.padle.core.padelcoreservice.dto.americano.RankingCriteria;
import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchResultDto;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.padelcoreservice.service.EmailService;
import com.padle.core.padelcoreservice.service.TournamentNotificationService;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import com.padle.core.padelcoreservice.service.conduct.AdditionalPointsService;
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
import jakarta.persistence.EntityManager;
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
        "spring.datasource.url=${RANKING_QA_DB_URL:jdbc:postgresql://${BROWSER_QA_DB_HOST:localhost}:5432/padel_browser_qa}",
        "spring.datasource.username=${RANKING_QA_DB_USER:epadel_user}",
        "spring.datasource.password=${RANKING_QA_DB_PASSWORD:test}",
        "telegram.enabled=false", "telegram.players.enabled=false", "app.analytics.meta.enabled=false",
        "app.analytics.ga4-id=", "app.analytics.clarity-id=",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false", "spring.mail.username=", "spring.mail.password="
})
class IndividualRankingBrowserTest {
    @LocalServerPort int port;
    @Autowired OwnerFixtures owners;
    @Autowired PlayerRepository players;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRepository tournaments;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @Autowired EntityManager entityManager;
    @Autowired KingOfCourtService king;
    @Autowired AmericanoService americano;
    @Autowired AdditionalPointsService points;
    @MockitoBean EmailService email;
    @MockitoBean TournamentNotificationService notifications;

    record Account(String email, String role) {}
    record Events(long kingEvent, long kingId, long americanoEvent) {}
    record PublicScenario(int count, Events events, boolean finished, long playerId, String playerEmail) {}
    record Fixture(String baseUrl, String password, List<Account> accounts, Events clubEvents, Events organizerEvents, List<PublicScenario> publicScenarios) {}

    @Test void standingsWorkAcrossFormatsWidthsLanguagesAndExistingRights() throws Exception {
        Fixture fixture = new TransactionTemplate(transactions).execute(status -> seed());
        assertThat(fixture).isNotNull();
        Path directory = Files.createTempDirectory("padel-ranking-browser-");
        Path input = directory.resolve("fixture.json");
        Path output = Path.of("target/individual-ranking-qa");
        Files.createDirectories(output);
        Files.writeString(input, json.writeValueAsString(fixture));
        try {
            var process = new ProcessBuilder("node", "tests/browser/individual-ranking.browser.mjs", input.toString())
                    .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            boolean completed = process.waitFor(600, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).as("Браузерная проверка рейтинга завершилась вовремя").isTrue();
            assertThat(process.exitValue()).as(Files.readString(output.resolve("browser.log"))).isZero();
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(directory);
        }
    }

    private Fixture seed() {
        String suffix = UUID.randomUUID().toString();
        String password = "Ranking-qa-" + suffix;
        var accounts = List.of(OwnerRole.SUPER_ADMIN, OwnerRole.ADMIN, OwnerRole.CLUB_ADMIN, OwnerRole.ORGANIZER).stream()
                .map(role -> owners.saveAndFlush(Owner.builder().email(role.name().toLowerCase() + suffix + "@example.test")
                        .password(passwords.encode(password)).firstName("Ranking QA").lastName(role.name())
                        .role(role).isActive(true).build())).toList();
        var club = clubs.saveAndFlush(Club.builder().nombre("Ranking QA " + suffix).isActive(true).build());
        var roster = IntStream.range(0, 8).mapToObj(index -> players.saveAndFlush(PlayerPadel.builder()
                .nombre(index == 0 ? "Александр-Максимилиан" : "Jugador " + index)
                .apellido(index == 0 ? "Константинопольский-Фернандес" : "Ejemplo")
                .email(index + suffix + "@example.test").passwordHash(passwords.encode(password))
                .activo(true).emailConfirmado(true).preferredLocale("es").nivelJugador(Nivel.C7)
                .playingSide(index % 4 == 0 ? null : PlayingSide.values()[index % 3]).build())).toList();
        var clubEvents = events(club, accounts.get(2), roster);
        var organizerEvents = events(club, accounts.get(3), roster);
        var publicRoster = IntStream.range(0, 24).mapToObj(index -> players.saveAndFlush(PlayerPadel.builder()
                .nombre(index == 23 ? "Александр-Максимилиан" : "Public Player " + index)
                .apellido(index == 23 ? "Константинопольский-Фернандес" : "Example")
                .email("public" + index + suffix + "@example.test").passwordHash(passwords.encode(password))
                .activo(true).emailConfirmado(true).preferredLocale("es").nivelJugador(Nivel.C7)
                .playingSide(PlayingSide.RIGHT).build())).toList();
        var publicScenarios = List.of(16, 20, 24, 8).stream().map(count -> {
            var run = events(club, accounts.get(2), publicRoster.subList(0, count));
            if (count == 8) finish(run, accounts.get(2));
            return new PublicScenario(count, run, count == 8, publicRoster.get(0).getId(), publicRoster.get(0).getEmail());
        }).toList();
        return new Fixture("http://127.0.0.1:" + port, password,
                accounts.stream().map(owner -> new Account(owner.getEmail(), owner.getRole().name())).toList(), clubEvents, organizerEvents, publicScenarios);
    }

    private Events events(Club club, Owner owner, List<PlayerPadel> roster) {
        var kingEvent = event(club, owner, TournamentType.KING_OF_COURT, roster);
        long kingId = king.initializeTournament(owner, kingEvent.getId(), roster.size() / 4, 0).getId();
        var state = king.getCurrentState(kingId);
        var court = state.getCourts().get(0);
        king.saveMatchResult(state.getCurrentRoundId(), court.getCourtNumber(),
                court.getTeams().get(0).getPlayers().stream().map(player -> player.getId()).toList(),
                court.getTeams().get(1).getPlayers().stream().map(player -> player.getId()).toList(), 12, 9, true);
        points.add(kingEvent.getId(), false, new AdditionalPointsService.Change(roster.get(0).getId(), 0, -20, "QA negative"), owner);
        points.add(kingEvent.getId(), false, new AdditionalPointsService.Change(roster.get(1).getId(), 0, 5, "QA positive"), owner);
        var americanoEvent = event(club, owner, TournamentType.AMERICANO, roster);
        americano.initializeAmericanoTournament(americanoEvent.getId(),
                AmericanoConfigDto.builder().courts(roster.size() / 4).totalRounds(3).pointsPerMatch(21).build(), owner);
        var round = americano.getRounds(americanoEvent.getId()).get(0);
        var match = americano.getMatchesByRound(round.getId()).get(0);
        americano.submitMatchResult(AmericanoMatchResultDto.builder().matchId(match.getId()).team1Score(12).team2Score(9).build(), owner);
        points.add(americanoEvent.getId(), false, new AdditionalPointsService.Change(roster.get(0).getId(), 0, -20, "QA negative"), owner);
        points.add(americanoEvent.getId(), false, new AdditionalPointsService.Change(roster.get(1).getId(), 0, 5, "QA positive"), owner);
        return new Events(kingEvent.getId(), kingId, americanoEvent.getId());
    }

    private void finish(Events run, Owner owner) {
        var state = king.getCurrentState(run.kingId());
        state.getCourts().stream().filter(court -> !Boolean.TRUE.equals(court.getHasResult())).forEach(court ->
                king.saveMatchResult(state.getCurrentRoundId(), court.getCourtNumber(),
                        court.getTeams().get(0).getPlayers().stream().map(player -> player.getId()).toList(),
                        court.getTeams().get(1).getPlayers().stream().map(player -> player.getId()).toList(), 12, 9, true));
        king.finishTournament(run.kingId());
        for (var round : americano.getRounds(run.americanoEvent())) {
            if (round.getStatus() == AmericanoRoundStatus.PENDING) americano.startRound(round.getId(), owner);
            americano.getMatchesByRound(round.getId()).forEach(match -> americano.submitMatchResult(
                    AmericanoMatchResultDto.builder().matchId(match.getId()).team1Score(12).team2Score(9).build(), owner));
        }
        americano.finishTournament(run.americanoEvent(), RankingCriteria.builder().sortBy("score").build(), owner);
    }

    private Tournament event(Club club, Owner owner, TournamentType type, List<PlayerPadel> roster) {
        var event = tournaments.saveAndFlush(Tournament.builder().nombre("Ranking QA " + type)
                .clubId(club.getId()).ownerId(owner.getId()).fechaInicio(LocalDate.now().plusDays(7)).horaInicio(LocalTime.NOON)
                .generoFormato(GenderFormat.MIXTO).categoriaNivel(Nivel.C7).tipo(type).modalidad(Modalidad.INDIVIDUAL)
                .contactoOrganizador("qa@example.test").cupoMax(roster.size()).mostrarNivel(true).precio(BigDecimal.ZERO).estado(TournamentStatus.CERRADO).isActive(true).build());
        IntStream.range(0, roster.size()).forEach(index -> registrations.saveAndFlush(TournamentRegistration.builder()
                .tournament(event).player(roster.get(index)).status(RegistrationStatus.CONFIRMED).position(index + 1).build()));
        entityManager.flush();
        entityManager.clear();
        return event;
    }
}
