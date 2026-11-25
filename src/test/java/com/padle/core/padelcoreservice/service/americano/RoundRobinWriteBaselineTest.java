package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.application.roundrobin.RoundRobinFinishCommand;
import com.padle.core.application.roundrobin.RoundRobinPositionCommand;
import com.padle.core.application.roundrobin.RoundRobinRankingQuery;
import com.padle.core.padelcoreservice.adapter.roundrobin.LegacyRoundRobinFinishAdapter;
import com.padle.core.padelcoreservice.adapter.roundrobin.LegacyRoundRobinPositionAdapter;
import com.padle.core.padelcoreservice.dto.americano.RoundRobinAdditionalMatchRequest;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoConfigDto;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.OwnerRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import com.padle.core.padelcoreservice.service.conduct.TournamentConductHistory;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.test", "spring.mail.password=test",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test", "app.scheduling.enabled=false"
})
class RoundRobinWriteBaselineTest {
    @Autowired TeamAmericanoService service;
    @Autowired RoundRobinAdditionalMatches additional;
    @Autowired RoundRobinRankingQuery query;
    @Autowired RoundRobinPositionCommand positionCommand;
    @Autowired RoundRobinFinishCommand finishCommand;
    @MockitoSpyBean LegacyRoundRobinFinishAdapter finishAdapter;
    @MockitoSpyBean LegacyRoundRobinPositionAdapter positionAdapter;
    @Autowired TournamentConductHistory history;
    @Autowired TournamentRepository tournaments;
    @Autowired ClubRepository clubs;
    @Autowired OwnerRepository owners;
    @Autowired PlayerRepository players;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired AmericanoTeamRepository teams;
    @Autowired AmericanoRoundRepository rounds;
    @Autowired AmericanoMatchRepository matches;
    @Autowired EntityManager entities;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource source;
    @Autowired MockMvc mvc;
    @MockitoSpyBean JdbcClient jdbc;
    @MockitoBean SimpMessagingTemplate broker;
    private final List<Fixture> fixtures = new ArrayList<>();
    private final List<Probe> probes = new ArrayList<>();
    private final ConcurrentLinkedQueue<Sent> sent = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Context> contexts = new ConcurrentLinkedQueue<>();
    private volatile Fault fault = Fault.NONE;
    private volatile long faultId;
    private volatile boolean observing;
    private Tournament finishEvent;
    private boolean markingFinished;
    private final AtomicBoolean reached = new AtomicBoolean();
    private final AtomicBoolean paused = new AtomicBoolean();
    private volatile CountDownLatch entered = new CountDownLatch(0);
    private volatile CountDownLatch release = new CountDownLatch(0);

    @BeforeEach
    void observeRealBoundaries() {
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (fault == Fault.HISTORY && sql.stripLeading().startsWith("INSERT INTO tournament_conduct_history")) {
                failAfterFlush(Fault.HISTORY);
            }
            return invocation.callRealMethod();
        }).when(jdbc).sql(anyString());
        doAnswer(invocation -> {
            String destination = invocation.getArgument(0);
            if (destination.startsWith("/topic/public/live/")) {
                long id = Long.parseLong(destination.substring(destination.lastIndexOf('/') + 1));
                sent.add(new Sent(id, committedState(id)));
            }
            return null; // Транспорт брокера заменён на наблюдатель границы после commit.
        }).when(broker).convertAndSend(anyString(), any(Object.class));
        observe(teams, "teams");
        observe(tournaments, "event");
        observe(rounds, "rounds");
        observe(query, "query");
        observe(positionCommand, "position-command");
        observe(finishCommand, "finish-command");
        doAnswer(invocation -> {
            captureContext("finish-port.findEvent");
            return invocation.callRealMethod();
        }).when(finishAdapter).findEvent(any(Long.class));
        doAnswer(invocation -> {
            captureContext("finish-port.markFinished");
            markingFinished = observing;
            try {
                return invocation.callRealMethod();
            } finally {
                markingFinished = false;
            }
        }).when(finishAdapter).markFinished(any(Long.class));
        doAnswer(invocation -> {
            captureContext("position-port.load");
            return invocation.callRealMethod();
        }).when(positionAdapter).load(any(Long.class));
        doAnswer(invocation -> {
            captureContext("position-port.savePositions");
            return invocation.callRealMethod();
        }).when(positionAdapter).savePositions(any());
    }

    @AfterEach
    void releaseOwnedResources() {
        fault = Fault.NONE;
        observing = false;
        release.countDown();
        probes.forEach(probe -> probe.target().removeAdvice(probe.interceptor()));
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        tx(() -> {
            fixtures.forEach(fixture -> {
                jdbc.sql("DELETE FROM marketing_events_db WHERE tournament_id=:id").param("id", fixture.id()).update();
                jdbc.sql("DELETE FROM marketing_paid_slots_db WHERE registration_id IN "
                        + "(SELECT id FROM tournament_registrations_db WHERE tournament_id=:id)")
                        .param("id", fixture.id()).update();
                jdbc.sql("DELETE FROM marketing_registrations_db WHERE registration_id IN "
                        + "(SELECT id FROM tournament_registrations_db WHERE tournament_id=:id)")
                        .param("id", fixture.id()).update();
                for (String table : List.of("tournament_conduct_history", "americano_matches_db", "americano_rounds_db",
                        "americano_teams_db", "tournament_registrations_db")) {
                    jdbc.sql("DELETE FROM " + table + " WHERE tournament_id=:id").param("id", fixture.id()).update();
                }
                tournaments.deleteById(fixture.id());
                entities.flush();
                fixture.players().forEach(players::deleteById);
                clubs.deleteById(fixture.club());
                owners.deleteById(fixture.owner().getId());
            });
            return true;
        });
    }

    @ParameterizedTest
    @EnumSource(RoundRobinScoringMode.class)
    void standalonePositionsPersistOnlyPlacesAndDoNotCreateHistory(RoundRobinScoringMode mode) {
        var fixture = fixture(4, mode, true);
        String fields = teamFields(fixture.id());
        long version = revision(fixture.id());
        observeOnlyScenario();
        service.updateTeamPositions(fixture.id());
        assertThat(positions(fixture.id())).containsExactly(1, 2, 3, 4);
        assertThat(teamFields(fixture.id())).isEqualTo(fields);
        assertThat(revision(fixture.id())).isEqualTo(version);
        assertThat(sent).isEmpty();
        assertSingleWriteContext();
    }

    @ParameterizedTest
    @EnumSource(RoundRobinScoringMode.class)
    void finishKeepsCircleAndPlacementSeparateAndNotifiesAfterCommit(RoundRobinScoringMode mode) {
        var fixture = fixture(2, mode, true);
        tx(() -> {
            var ranked = service.getRanking(fixture.id()).getRanking();
            var extra = additional.add(fixture.id(), new RoundRobinAdditionalMatchRequest(
                    ranked.get(0).getId(), ranked.get(1).getId(), 1, 1, "QA final", UUID.randomUUID()));
            service.submitMatchResult(extra.getId(), mode == RoundRobinScoringMode.SETS ? 4 : 14,
                    mode == RoundRobinScoringMode.SETS ? 6 : 18);
            teams.findByTournamentId(fixture.id()).forEach(team -> team.setCurrentPosition(9));
            return true;
        });
        String fields = teamFields(fixture.id());
        observeOnlyScenario();
        var result = service.finishTournament(fixture.id());
        assertThat(result.getRanking()).extracting(team -> team.getTeamNumber()).containsExactly(1, 2);
        assertThat(result.getFinalRanking()).extracting(team -> team.teamNumber()).containsExactly(2, 1);
        assertThat(positions(fixture.id())).containsExactly(1, 2);
        assertThat(teamFields(fixture.id())).isEqualTo(fields);
        assertThat(sent).containsExactly(new Sent(fixture.id(), "FINALIZADO"));
        assertSingleWriteContext();
        assertFinishBoundaries(true);
        assertThat(contexts).extracting(Context::boundary).containsSubsequence("event.save", "query.ranking");
    }

    @ParameterizedTest
    @EnumSource(value = Fault.class, names = {"POSITIONS", "STATE", "QUERY", "HISTORY"})
    void boundaryFailureRollsBackFlushedPositionsStateAndHistory(Fault boundary) {
        var fixture = fixture(4, RoundRobinScoringMode.SETS, true);
        var before = stored(fixture.id());
        faultId = fixture.id();
        fault = boundary;
        observeOnlyScenario();
        assertThatThrownBy(() -> service.finishTournament(fixture.id())).isInstanceOf(WriteProbe.class)
                .hasMessage(boundary.name());
        fault = Fault.NONE;
        observing = false;
        assertThat(reached).isTrue();
        assertThat(stored(fixture.id())).isEqualTo(before);
        assertThat(sent).isEmpty();
        assertSingleWriteContext();
        assertFinishBoundaries(boundary != Fault.POSITIONS);
    }

    @Test
    void completedRoundCorrectionLeavesStoredPlacesUntilExplicitUpdate() {
        var fixture = fixture(2, RoundRobinScoringMode.POINTS, false);
        long match = tx(() -> matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(fixture.id()).get(0).getId());
        boolean firstWins = firstTeamNumber(match) == 1;
        observeOnlyScenario();
        service.submitMatchResult(match, firstWins ? 18 : 14, firstWins ? 14 : 18);
        var first = positions(fixture.id());
        assertThat(first).containsExactly(1, 2);
        contexts.clear();
        service.submitMatchResult(match, firstWins ? 14 : 18, firstWins ? 18 : 14);
        assertThat(positions(fixture.id())).isEqualTo(first);
        assertThat(service.getRanking(fixture.id()).getRanking()).extracting(team -> team.getTeamNumber())
                .containsExactly(2, 1);
        service.finishTournament(fixture.id());
        assertThat(positions(fixture.id())).containsExactly(2, 1);
    }

    @Test
    void standaloneFailureRollsBackWithoutAnOuterConductTransaction() {
        var fixture = fixture(4, RoundRobinScoringMode.POINTS, true);
        var before = stored(fixture.id());
        faultId = fixture.id();
        fault = Fault.POSITIONS;
        assertThatThrownBy(() -> service.updateTeamPositions(fixture.id())).isInstanceOf(WriteProbe.class);
        fault = Fault.NONE;
        assertThat(reached).isTrue();
        assertThat(stored(fixture.id())).isEqualTo(before);
        assertThat(sent).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"form", "json", "round-robin"})
    void scoreEntrypointsAndFinishKeepOwnershipCsrfAndTheirResponseShapes(String route) throws Exception {
        var fixture = fixture(2, RoundRobinScoringMode.POINTS, false);
        var foreign = fixture(0, RoundRobinScoringMode.POINTS, false).owner();
        foreign.setRole(OwnerRole.CLUB_ADMIN);
        foreign.setClubId(fixture.club());
        long match = tx(() -> matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(fixture.id()).get(0).getId());
        String path = switch (route) {
            case "form" -> "/tournaments/team-americano/matches/" + match + "/result";
            case "json" -> "/tournaments/team-americano/api/matches/" + match + "/result";
            default -> "/tournaments/team-americano/api/round-robin/matches/" + match + "/result";
        };
        var before = stored(fixture.id());
        for (String endpoint : List.of(path, finishPath(fixture.id()))) {
            mvc.perform(scoreRequest(endpoint, route).with(user(foreign)).with(csrf()).accept("application/json"))
                    .andExpect(status().isForbidden());
            mvc.perform(scoreRequest(endpoint, route).with(user("write-player").roles("PLAYER")).with(csrf()).accept("application/json"))
                    .andExpect(status().isForbidden());
            mvc.perform(scoreRequest(endpoint, route).with(csrf()).accept("application/json"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(scoreRequest(endpoint, route).with(user(fixture.owner())).accept("application/json"))
                    .andExpect(status().isForbidden());
        }
        assertThat(stored(fixture.id())).isEqualTo(before);
        var valid = post(path).with(user(fixture.owner())).with(csrf());
        int firstScore = firstTeamNumber(match) == 1 ? 18 : 14;
        int secondScore = 32 - firstScore;
        if (route.equals("form")) {
            mvc.perform(valid.param("team1Score", Integer.toString(firstScore)).param("team2Score", Integer.toString(secondScore)))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(adminPath(fixture.id())));
        } else {
            mvc.perform(valid.contentType("application/json").content(
                            "{\"team1Score\":%d,\"team2Score\":%d}".formatted(firstScore, secondScore)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        }
        assertThat(positions(fixture.id())).containsExactly(1, 2);
        mvc.perform(post(finishPath(fixture.id())).with(user(fixture.owner())).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(adminPath(fixture.id())))
                .andExpect(flash().attributeExists("success"));
        assertThat(committedStateUnchecked(fixture.id())).isEqualTo("FINALIZADO");
    }

    @Test
    void pendingPlacementAndNumberGapsUseMaximumNumberAndAllCompletedRounds() {
        var fixture = fixture(2, RoundRobinScoringMode.POINTS, true);
        tx(() -> {
            rounds.findByTournamentIdOrderByRoundNumberAsc(fixture.id()).get(0).setRoundNumber(3);
            return true;
        });
        var before = stored(fixture.id());
        sent.clear();
        assertThatThrownBy(() -> service.finishTournament(fixture.id())).isInstanceOf(InvalidStateException.class)
                .hasMessage("No se puede finalizar. Quedan 2 rondas sin completar.");
        assertThat(stored(fixture.id())).isEqualTo(before);
        assertThat(sent).isEmpty();
        tx(() -> {
            rounds.findByTournamentIdOrderByRoundNumberAsc(fixture.id()).get(0).setRoundNumber(1);
            var ranking = service.getRanking(fixture.id()).getRanking();
            additional.add(fixture.id(), new RoundRobinAdditionalMatchRequest(ranking.get(0).getId(),
                    ranking.get(1).getId(), 1, 1, "Pending", UUID.randomUUID()));
            return true;
        });
        var pending = stored(fixture.id());
        sent.clear();
        assertThatThrownBy(() -> service.finishTournament(fixture.id())).isInstanceOf(InvalidStateException.class)
                .hasMessage("No se puede finalizar. Quedan 1 rondas sin completar.");
        assertThat(stored(fixture.id())).isEqualTo(pending);
        assertThat(sent).isEmpty();
    }

    @Test
    void cancelledUninitializedAndMissingFinishDoNotLeaveBaselineRows() {
        var fixture = fixture(0, RoundRobinScoringMode.POINTS, false);
        var before = stored(fixture.id());
        assertThatThrownBy(() -> service.finishTournament(fixture.id())).isInstanceOf(InvalidStateException.class);
        assertThat(stored(fixture.id())).isEqualTo(before);
        tx(() -> { tournaments.findById(fixture.id()).orElseThrow().setEstado(TournamentStatus.CANCELADO); return true; });
        var cancelled = stored(fixture.id());
        assertThatThrownBy(() -> service.finishTournament(fixture.id())).isInstanceOf(InvalidStateException.class)
                .hasMessage("round_robin.error.finished");
        assertThat(stored(fixture.id())).isEqualTo(cancelled);
        assertThatThrownBy(() -> service.finishTournament(Long.MAX_VALUE)).isInstanceOf(ResourceNotFoundException.class);
        service.updateTeamPositions(Long.MAX_VALUE);
        assertThat(sent).isEmpty();
    }

    @Test
    void repeatWithoutKeyIsUnchangedButNewKeyRecordsAnotherFinish() {
        var fixture = fixture(2, RoundRobinScoringMode.POINTS, true);
        service.finishTournament(fixture.id());
        var before = stored(fixture.id());
        long version = revision(fixture.id());
        sent.clear();
        service.finishTournament(fixture.id());
        assertThat(stored(fixture.id())).isEqualTo(before);
        assertThat(sent).isEmpty();
        request(fixture, UUID.randomUUID(), version, () -> service.finishTournament(fixture.id()));
        assertThat(revision(fixture.id())).isGreaterThan(version);
        assertThat(sent).containsExactly(new Sent(fixture.id(), "FINALIZADO"));
    }

    @Test
    void outerWriteJoinsBothCommandsAndCoalescesNotificationsUntilCommit() {
        var fixture = fixture(2, RoundRobinScoringMode.POINTS, true);
        long before = finishCount(fixture.id());
        observeOnlyScenario();
        tx(() -> {
            service.finishTournament(fixture.id());
            assertThat(sent).isEmpty();
            request(fixture, UUID.randomUUID(), revision(fixture.id()), () -> service.finishTournament(fixture.id()));
            assertThat(sent).isEmpty();
            return true;
        });
        assertThat(finishCount(fixture.id())).isEqualTo(before + 2);
        assertThat(sent).containsExactly(new Sent(fixture.id(), "FINALIZADO"));
        assertSingleWriteContext();
    }

    @Test
    void replayAndStaleRejectBeforeWritesWhileMvcKeepsItsRedirectContract() throws Exception {
        var fixture = fixture(2, RoundRobinScoringMode.POINTS, true);
        long expected = revision(fixture.id());
        var key = UUID.randomUUID();
        request(fixture, key, expected, () -> service.finishTournament(fixture.id()));
        var before = stored(fixture.id());
        sent.clear();
        assertThatThrownBy(() -> request(fixture, key, expected, () -> service.finishTournament(fixture.id())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> request(fixture, UUID.randomUUID(), expected,
                () -> service.finishTournament(fixture.id()))).isInstanceOf(InvalidStateException.class)
                .hasMessage("conduct.history.stale");
        mvc.perform(post(finishPath(fixture.id())).with(user(fixture.owner())).with(csrf())
                        .header("Idempotency-Key", key).param("expectedConductVersion", Long.toString(expected)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(adminPath(fixture.id())))
                .andExpect(header().string("X-Conduct-Replayed", "true")).andExpect(flash().attributeExists("error"));
        mvc.perform(post(finishPath(fixture.id())).with(user(fixture.owner())).with(csrf())
                        .param("expectedConductVersion", Long.toString(expected)))
                .andExpect(status().is3xxRedirection()).andExpect(header().string("X-Conduct-Stale", "true"));
        assertThat(stored(fixture.id())).isEqualTo(before);
        assertThat(sent).isEmpty();
    }

    @Test
    void restoreKeepsLateVersionsAndRegistrationsThenAllowsFinishAgain() {
        var fixture = fixture(2, RoundRobinScoringMode.SETS, true);
        long chosen = revision(fixture.id());
        String registrationRows = fingerprint("tournament_registrations_db", fixture.id());
        service.finishTournament(fixture.id());
        long finished = revision(fixture.id());
        tx(() -> { history.restore(fixture.id(), chosen, finished, fixture.owner()); return true; });
        assertThat(revision(fixture.id())).isGreaterThan(finished);
        assertThat(committedStateUnchecked(fixture.id())).isEqualTo("CERRADO");
        assertThat(jdbc.sql("SELECT count(*) FROM tournament_conduct_history WHERE id=:id")
                .param("id", finished).query(Long.class).single()).isEqualTo(1);
        assertThat(fingerprint("tournament_registrations_db", fixture.id())).isEqualTo(registrationRows);
        service.finishTournament(fixture.id());
        assertThat(committedStateUnchecked(fixture.id())).isEqualTo("FINALIZADO");
    }

    @Test
    void tournamentLockSerializesTwoFinishTransactionsBeforeVersionCheck() throws Exception {
        var fixture = fixture(2, RoundRobinScoringMode.POINTS, true);
        long expected = revision(fixture.id());
        faultId = fixture.id();
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> request(fixture, UUID.randomUUID(), expected,
                    () -> service.finishTournament(fixture.id())));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> request(fixture, UUID.randomUUID(), expected,
                    () -> service.finishTournament(fixture.id())));
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (!blocked() && System.nanoTime() < end) {
                Thread.sleep(25);
            }
            assertThat(blocked()).isTrue();
            assertThat(second.isDone()).isFalse();
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).isFinished()).isTrue();
            assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(InvalidStateException.class).hasRootCauseMessage("conduct.history.stale");
            assertThat(sent).containsExactly(new Sent(fixture.id(), "FINALIZADO"));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void observe(Object bean, String name) {
        var target = (Advised) bean;
        MethodInterceptor interceptor = invocation -> {
            String method = invocation.getMethod().getName();
            captureContext(name + "." + method);
            if (markingFinished && name.equals("event") && method.equals("save")) {
                Tournament event = (Tournament) invocation.getArguments()[0];
                assertThat(event).isSameAs(finishEvent);
                assertThat(entities.contains(event)).isTrue();
                assertThat(event.getEstado()).isEqualTo(TournamentStatus.FINALIZADO);
            }
            if (name.equals("query") && method.equals("ranking")) {
                if (fault == Fault.QUERY) {
                    failAfterFlush(Fault.QUERY);
                }
                if (release.getCount() > 0 && paused.compareAndSet(false, true)) {
                    entered.countDown();
                    assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
                }
            }
            Object result = invocation.proceed();
            if (observing && name.equals("event") && method.equals("findById")
                    && result instanceof java.util.Optional<?> found && found.isPresent()) {
                finishEvent = (Tournament) found.get();
            }
            if (name.equals("teams") && method.equals("saveAll") && fault == Fault.POSITIONS) {
                failAfterFlush(Fault.POSITIONS);
            }
            if (name.equals("event") && method.equals("save") && fault == Fault.STATE) {
                failAfterFlush(Fault.STATE);
            }
            return result;
        };
        target.addAdvice(0, interceptor);
        probes.add(new Probe(target, interceptor));
    }

    private void failAfterFlush(Fault boundary) {
        entities.flush();
        assertThat(positions(faultId)).doesNotContain(9);
        if (boundary != Fault.POSITIONS) {
            assertThat(jdbc.sql("SELECT estado FROM tournaments_db WHERE id=:id").param("id", faultId)
                    .query(String.class).single()).isEqualTo("FINALIZADO");
        }
        reached.set(true);
        throw new WriteProbe(boundary.name());
    }

    private void observeOnlyScenario() {
        sent.clear();
        contexts.clear();
        finishEvent = null;
        observing = true;
    }

    private void assertSingleWriteContext() {
        assertThat(contexts).isNotEmpty();
        assertThat(contexts).extracting(Context::boundary)
                .contains("position-command.update", "position-port.load", "position-port.savePositions");
        var first = contexts.element().manager();
        assertThat(contexts).allSatisfy(context -> {
            assertThat(context.manager()).isSameAs(first);
            assertThat(context.readOnly()).isFalse();
        });
    }

    private void assertFinishBoundaries(boolean reachedStateSave) {
        var boundaries = contexts.stream().map(Context::boundary).toList();
        assertThat(boundaries).containsSubsequence("finish-command.finish", "finish-port.findEvent",
                "position-command.update", "position-port.savePositions");
        if (reachedStateSave) {
            assertThat(boundaries).containsSubsequence("position-port.savePositions", "finish-port.markFinished", "event.save");
        } else {
            assertThat(boundaries).doesNotContain("finish-port.markFinished", "query.ranking");
        }
    }

    private void captureContext(String boundary) {
        if (observing) {
            var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(entities.getEntityManagerFactory());
            assertThat(holder).isNotNull();
            contexts.add(new Context(boundary, holder.getEntityManager(),
                    TransactionSynchronizationManager.isCurrentTransactionReadOnly()));
        }
    }

    private Fixture fixture(int count, RoundRobinScoringMode mode, boolean complete) {
        var fixture = tx(() -> {
            var suffix = UUID.randomUUID().toString();
            var owner = owners.save(Owner.builder().firstName("Write").lastName("QA")
                    .email("rr-write-" + suffix + "@example.test").password("test").role(OwnerRole.ORGANIZER).build());
            var club = clubs.save(Club.builder().nombre("Write QA " + suffix).isActive(true).build());
            var event = tournaments.save(Tournament.builder().clubId(club.getId()).ownerId(owner.getId()).nombre("Write QA")
                    .fechaInicio(LocalDate.now().plusDays(2)).horaInicio(LocalTime.NOON).generoFormato(GenderFormat.MIXTO)
                    .categoriaNivel(Nivel.C7).tipo(TournamentType.AMERICANO_TEAMS).modalidad(Modalidad.DOBLES)
                    .cupoMax(Math.max(2, count)).precio(BigDecimal.ZERO).estado(TournamentStatus.CERRADO)
                    .contactoOrganizador("qa@example.test").isActive(true).build());
            var people = IntStream.range(0, count * 2).mapToObj(index -> players.save(PlayerPadel.builder()
                    .nombre("Write").apellido("Player " + index).email("rr-write-" + suffix + "-" + index + "@example.test")
                    .passwordHash("test").activo(true).emailConfirmado(true).oauth2User(false)
                    .preferredLocale("es").nivelJugador(Nivel.C7).build())).toList();
            IntStream.range(0, count).forEach(index -> {
                var first = people.get(index * 2);
                var second = people.get(index * 2 + 1);
                for (var player : List.of(first, second)) {
                    registrations.save(TournamentRegistration.builder().tournament(event).player(player)
                            .status(RegistrationStatus.CONFIRMED).isDoubleRegistration(true).mainPlayerId(first.getId())
                            .partner(player == first ? second : first).position(index + 1).build());
                }
            });
            if (count > 0) {
                var config = new TeamAmericanoConfigDto();
                config.setCourts(2);
                config.setScoringMode(mode);
                config.setPointsPerMatch(32);
                service.initialize(event.getId(), config);
                var ordered = teams.findByTournamentId(event.getId()).stream()
                        .sorted(Comparator.comparing(team -> team.getPlayer1().getId())).toList();
                IntStream.range(0, ordered.size()).forEach(index -> ordered.get(index).setTeamNumber(index + 1));
                if (complete) {
                    matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(event.getId()).forEach(match -> {
                        boolean first = ordered.stream().filter(team -> team.getId().equals(match.getTeam1Id()))
                                .findFirst().orElseThrow().getTeamNumber() < ordered.stream()
                                .filter(team -> team.getId().equals(match.getTeam2Id())).findFirst().orElseThrow().getTeamNumber();
                        int win = mode == RoundRobinScoringMode.SETS ? 6 : 18;
                        int lose = mode == RoundRobinScoringMode.SETS ? 4 : 14;
                        service.submitMatchResult(match.getId(), first ? win : lose, first ? lose : win);
                    });
                }
                ordered.forEach(team -> team.setCurrentPosition(9));
            }
            entities.flush();
            return new Fixture(event.getId(), club.getId(), owner, people.stream().map(PlayerPadel::getId).toList());
        });
        fixtures.add(fixture);
        sent.clear();
        return fixture;
    }

    private <T> T tx(Supplier<T> work) {
        return new TransactionTemplate(transactions).execute(status -> work.get());
    }

    private <T> T request(Fixture fixture, UUID key, long version, Supplier<T> work) {
        var request = new MockHttpServletRequest();
        request.addHeader("Idempotency-Key", key.toString());
        request.setParameter("expectedConductVersion", Long.toString(version));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request, new MockHttpServletResponse()));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                fixture.owner(), "test", fixture.owner().getAuthorities()));
        try {
            return work.get();
        } finally {
            RequestContextHolder.resetRequestAttributes();
            SecurityContextHolder.clearContext();
        }
    }

    private List<Integer> positions(long id) {
        return jdbc.sql("SELECT current_position FROM americano_teams_db WHERE tournament_id=:id ORDER BY team_number")
                .param("id", id).query(Integer.class).list();
    }

    private String teamFields(long id) {
        return jdbc.sql("SELECT md5(string_agg((to_jsonb(t)-'current_position'-'updated_at')::text,'|' ORDER BY id)) FROM americano_teams_db t WHERE tournament_id=:id")
                .param("id", id).query(String.class).single();
    }

    private long revision(long id) {
        return history.revision(id);
    }

    private long finishCount(long id) {
        return jdbc.sql("SELECT count(*) FROM tournament_conduct_history WHERE tournament_id=:id AND action='FINISH'")
                .param("id", id).query(Long.class).single();
    }

    private int firstTeamNumber(long match) {
        return tx(() -> teams.findById(matches.findById(match).orElseThrow().getTeam1Id())
                .orElseThrow().getTeamNumber());
    }

    private Stored stored(long id) {
        var rows = List.of("americano_teams_db", "americano_rounds_db", "americano_matches_db",
                "tournament_conduct_history", "tournament_registrations_db").stream()
                .map(table -> fingerprint(table, id)).toList();
        var event = jdbc.sql("SELECT md5(to_jsonb(t)::text) FROM tournaments_db t WHERE id=:id")
                .param("id", id).query(String.class).single();
        return new Stored(event, rows);
    }

    private String fingerprint(String table, long id) {
        return jdbc.sql("SELECT coalesce(md5(string_agg(to_jsonb(t)::text,'|' ORDER BY id)),'empty') FROM "
                + table + " t WHERE tournament_id=:id").param("id", id).query(String.class).single();
    }

    private String committedState(long id) throws SQLException {
        try (var connection = source.getConnection();
             var statement = connection.prepareStatement("SELECT estado FROM tournaments_db WHERE id=?")) {
            statement.setLong(1, id);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private String committedStateUnchecked(long id) {
        return tx(() -> tournaments.findById(id).orElseThrow().getEstado().name());
    }

    private boolean blocked() {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database() "
                + "AND query LIKE 'SELECT tipo FROM tournaments_db%FOR UPDATE%' AND cardinality(pg_blocking_pids(pid))>0)")
                .query(Boolean.class).single();
    }

    private String finishPath(long id) { return "/tournaments/team-americano/admin/" + id + "/finish"; }
    private String adminPath(long id) { return "/tournaments/team-americano/admin/" + id; }

    private MockHttpServletRequestBuilder scoreRequest(String path, String route) {
        var request = post(path);
        if (path.endsWith("/finish")) {
            return request;
        }
        return route.equals("form") ? request.param("team1Score", "18").param("team2Score", "14")
                : request.contentType("application/json").content("{\"team1Score\":18,\"team2Score\":14}");
    }
    private enum Fault { NONE, POSITIONS, STATE, QUERY, HISTORY }
    private record Fixture(long id, long club, Owner owner, List<Long> players) {}
    private record Stored(String event, List<String> rows) {}
    private record Sent(long id, String committedState) {}
    private record Context(String boundary, EntityManager manager, boolean readOnly) {}
    private record Probe(Advised target, MethodInterceptor interceptor) {}
    private static final class WriteProbe extends RuntimeException {
        WriteProbe(String boundary) { super(boundary); }
    }
}
