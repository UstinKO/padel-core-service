package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.preview.PlayoffPilotFixtureService.Accounts;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import com.padle.core.padelcoreservice.service.conduct.TournamentConductHistory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"recaptcha.enabled=false", "app.scheduling.enabled=false", "app.auth.google-enabled=false"})
@AutoConfigureMockMvc
@Import(PlayoffPilotFixtureIntegrationTest.Configuration.class)
class PlayoffPilotFixtureIntegrationTest {
    @Autowired private PlayoffPilotFixtureService fixtures;
    @Autowired private PreviewFixtureService preview;
    @Autowired private PreviewProperties properties;
    @Autowired private JdbcClient jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private TournamentRepository tournaments;
    @Autowired private AmericanoMatchRepository matches;
    @Autowired private TeamPlayoffService playoff;
    @Autowired private TournamentConductHistory history;
    @Autowired private TournamentAccessService access;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entities;
    @Autowired private AtomicBoolean rejectedBoundary;
    @MockitoSpyBean private PlayoffPilotData data;
    @MockitoBean private SimpMessagingTemplate messaging;

    @BeforeEach
    void prepareMarker() {
        rejectedBoundary.set(false);
        jdbc.sql("CREATE SCHEMA IF NOT EXISTS ui_preview").update();
        jdbc.sql("CREATE TABLE IF NOT EXISTS ui_preview.control(instance_id uuid PRIMARY KEY, state jsonb)").update();
        jdbc.sql("INSERT INTO ui_preview.control(instance_id,state) VALUES (:id,CAST(:state AS jsonb))")
                .param("id", properties.instanceId()).param("state", legacyState()).update();
    }

    @AfterEach
    void removeOnlyOwnFixtures() {
        fixtures.snapshot().ifPresent(state -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            state.scenarios().forEach(scenario -> preview.removeEvent(scenario.tournamentId()));
            entities.flush();
            jdbc.sql("DELETE FROM player_padel_db WHERE id IN (:ids)").param("ids", state.accounts().playerIds()).update();
            jdbc.sql("DELETE FROM owners_db WHERE id IN (:own,:foreign)")
                    .param("own", state.accounts().managerId()).param("foreign", state.accounts().foreignManagerId()).update();
            jdbc.sql("DELETE FROM club_db WHERE id IN (:own,:foreign)")
                    .param("own", state.accounts().clubId()).param("foreign", state.accounts().foreignClubId()).update();
        }));
        jdbc.sql("DELETE FROM ui_preview.control WHERE instance_id=:id").param("id", properties.instanceId()).update();
    }

    @Test
    void fixturesAreRepeatableQualifiedAndResetWithoutChangingLegacyMetadata() {
        assertThat(fixtures.snapshot()).isEmpty();
        var first = fixtures.seed();
        assertThat(fixtures.seed()).isEqualTo(first);
        assertThat(first.scenarios()).extracting(PlayoffPilotFixtureService.Scenario::key)
                .containsExactly("roster-8", "qualified-8", "qualified-9", "qualified-16", "finished-8");
        for (var scenario : first.scenarios()) {
            assertThat(scenario.teamIds()).hasSize(scenario.teamCount());
            if (scenario.stage() == PlayoffPilotFixtureService.Stage.QUALIFIED) {
                var played = matches.findByTournamentIdAndPhase(scenario.tournamentId(), TournamentPhase.QUALIFICATION);
                assertThat(played).hasSize(scenario.teamCount()).allMatch(match -> match.isCompleted());
                for (long team : scenario.teamIds()) {
                    assertThat(played.stream().filter(match -> match.getTeam1Id().equals(team)
                            || match.getTeam2Id().equals(team)).count()).isEqualTo(2);
                }
            }
        }
        assertThat(tournaments.findById(scenario(first, "finished-8")).orElseThrow().getEstado())
                .isEqualTo(TournamentStatus.FINALIZADO);
        var expectedResults = qualificationResults(first);
        var second = fixtures.reset();
        assertThat(qualificationResults(second)).isEqualTo(expectedResults);
        assertThat(second.accounts()).isEqualTo(first.accounts());
        assertThat(second.scenarios()).extracting(PlayoffPilotFixtureService.Scenario::tournamentId)
                .doesNotContainAnyElementsOf(first.scenarios().stream().map(PlayoffPilotFixtureService.Scenario::tournamentId).toList());
        assertThat(first.scenarios()).allMatch(scenario -> tournaments.findById(scenario.tournamentId()).isEmpty());
        assertThat(json.readValue(storedState(), PreviewFixtureService.Fixtures.class))
                .isEqualTo(json.readValue(legacyState(), PreviewFixtureService.Fixtures.class));
        assertThat(json.readValue(storedState(), LegacyFixtures.class))
                .isEqualTo(json.readValue(legacyState(), LegacyFixtures.class));
    }

    @Test
    void controllerRequiresSuperAdminAndCsrfAndGetDoesNotCreateData() throws Exception {
        String path = "/api/internal/ui-preview/playoff-pilot";
        mvc.perform(get(path).with(user("admin").roles("SUPER_ADMIN"))).andExpect(status().isNotFound());
        assertThat(fixtures.snapshot()).isEmpty();
        mvc.perform(post(path).with(user("admin").roles("SUPER_ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("club").roles("CLUB_ADMIN")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user("admin").roles("SUPER_ADMIN")).with(csrf()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        String state = storedState();
        mvc.perform(get(path).with(user("admin").roles("SUPER_ADMIN"))).andExpect(status().isOk());
        assertThat(storedState()).isEqualTo(state);
    }

    @Test
    void rollbackKeepsBracketAndHistoryTogetherAndDoesNotPublishSuccess() {
        long event = scenario(fixtures.seed(), "qualified-8");
        long version = history.revision(event);
        clearInvocations(messaging);
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            playoff.initPlayoff(event);
            assertThat(history.revision(event)).isGreaterThan(version);
            throw new IllegalStateException("Проверочный отказ после сохранения");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(matches.findByTournamentIdAndPhase(event, TournamentPhase.PLAYOFF)).isEmpty();
        assertThat(history.revision(event)).isEqualTo(version);
        verifyNoInteractions(messaging);
    }

    @Test
    void clubBoundaryAndStaleParticipantsKeepStoredState() {
        var state = fixtures.seed();
        long event = scenario(state, "qualified-8");
        Owner own = Owner.builder().id(state.accounts().managerId()).role(OwnerRole.CLUB_ADMIN)
                .clubId(state.accounts().clubId()).build();
        Owner foreign = Owner.builder().id(state.accounts().foreignManagerId()).role(OwnerRole.CLUB_ADMIN)
                .clubId(state.accounts().foreignClubId()).build();
        access.assertCanManageTournament(own, event);
        assertThatThrownBy(() -> access.assertCanManageTournament(foreign, event)).isInstanceOf(AccessDeniedException.class);
        playoff.initPlayoff(event);
        var match = matches.findByTournamentIdAndPhase(event, TournamentPhase.PLAYOFF).stream()
                .filter(item -> item.isInProgress()).findFirst().orElseThrow();
        long version = history.revision(event);
        assertThatThrownBy(() -> playoff.submitPlayoffResultForPair(match.getId(), 6, 3, -1L, -2L))
                .hasMessage("conduct.history.stale");
        assertThat(history.revision(event)).isEqualTo(version);
        assertThat(matches.findById(match.getId()).orElseThrow().isCompleted()).isFalse();
        var previous = history.list(event, own).get(0);
        assertThatThrownBy(() -> history.restore(event, previous.id(), version - 1, own))
                .hasMessage("conduct.history.stale");
        assertThat(history.revision(event)).isEqualTo(version);
    }

    @Test
    void resetRollsBackDeletedDataIfRecreationFailsAndBoundaryFailureDoesNotWrite() {
        var state = fixtures.seed();
        String stored = storedState();
        doThrow(new IllegalStateException("Проверочный отказ подготовки"))
                .when(data).scenarios(any(Accounts.class));
        assertThatThrownBy(fixtures::reset).isInstanceOf(IllegalStateException.class);
        assertThat(storedState()).isEqualTo(stored);
        assertThat(state.scenarios()).allMatch(scenario -> tournaments.existsById(scenario.tournamentId()));
        rejectedBoundary.set(true);
        assertThatThrownBy(fixtures::reset).hasMessage("Неверная граница preview");
        assertThat(storedState()).isEqualTo(stored);
    }

    private long scenario(PlayoffPilotFixtureService.Fixtures state, String key) {
        return state.scenarios().stream().filter(item -> key.equals(item.key())).findFirst().orElseThrow().tournamentId();
    }

    private String storedState() {
        return jdbc.sql("SELECT state::text FROM ui_preview.control WHERE instance_id=:id")
                .param("id", properties.instanceId()).query(String.class).single();
    }

    private List<String> qualificationResults(PlayoffPilotFixtureService.Fixtures state) {
        return state.scenarios().stream().flatMap(scenario -> matches
                .findByTournamentIdAndPhase(scenario.tournamentId(), TournamentPhase.QUALIFICATION).stream()
                .sorted(Comparator.comparing(AmericanoMatch::getId))
                .map(match -> scenario.key() + ":" + scenario.teamIds().indexOf(match.getTeam1Id()) + "-"
                        + scenario.teamIds().indexOf(match.getTeam2Id()) + ":" + match.getTeam1Games() + "-" + match.getTeam2Games()))
                .toList();
    }

    private String legacyState() {
        return "{\"ownerId\":1,\"clubId\":1,\"playerIds\":[],\"tournamentIds\":[]}";
    }

    @TestConfiguration
    static class Configuration {
        @Bean PreviewProperties pilotProperties() {
            return new PreviewProperties(UUID.randomUUID(), "0".repeat(40), UUID.randomUUID().toString());
        }
        @Bean AtomicBoolean rejectedBoundary() { return new AtomicBoolean(); }
        @Bean PreviewFixtureService previewBoundary(JdbcClient jdbc, PreviewProperties properties,
                PasswordEncoder passwords, ObjectMapper json, TournamentService tournaments, AtomicBoolean rejectedBoundary) {
            // Проверки реального имени БД/UUID выполняются отдельными boundary-тестами; здесь проверяется запись через реальные сервисы.
            return new PreviewFixtureService(jdbc, properties, passwords, json, tournaments) {
                @Override public Fixtures seed() {
                    if (rejectedBoundary.get()) throw new IllegalStateException("Неверная граница preview");
                    return new Fixtures(1, 1, List.of(), List.of());
                }
            };
        }
        @Bean PlayoffPilotData pilotData(JdbcClient jdbc, PreviewProperties properties, PasswordEncoder passwords,
                TeamPlayoffService playoff, AmericanoMatchRepository matches, TournamentRepository tournaments) {
            return new PlayoffPilotData(jdbc, properties, passwords, playoff, matches, tournaments);
        }
        @Bean PlayoffPilotFixtureService pilotFixtures(JdbcClient jdbc, PreviewProperties properties,
                PreviewFixtureService preview, PlayoffPilotData data, ObjectMapper json) {
            return new PlayoffPilotFixtureService(jdbc, properties, preview, data, json);
        }
        @Bean PlayoffPilotController pilotController(PlayoffPilotFixtureService fixtures) {
            return new PlayoffPilotController(fixtures);
        }
    }

    record LegacyFixtures(long ownerId, long clubId, List<Long> playerIds, List<Long> tournamentIds) { }
}
