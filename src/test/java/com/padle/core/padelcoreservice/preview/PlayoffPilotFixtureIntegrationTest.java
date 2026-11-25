package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoRankingDto;
import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffAddTeamRequest;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.PlayoffStage;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.preview.PlayoffPilotFixtureService.Accounts;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import com.padle.core.padelcoreservice.service.americano.PlayoffMatchingEngine;
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
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
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
import java.util.ArrayList;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = {"recaptcha.enabled=false", "app.scheduling.enabled=false", "app.auth.google-enabled=false"})
@AutoConfigureMockMvc
@Import(PlayoffPilotFixtureIntegrationTest.Configuration.class)
class PlayoffPilotFixtureIntegrationTest {
    @Autowired private PlayoffPilotFixtureService fixtures;
    @Autowired private ApplicationContext context;
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
        assertThat(context.getBeansOfType(PlayoffMatchingEngine.class)).containsOnlyKeys("playoffMatchingEngine");
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
        for (int reset = 0; reset < 3; reset++) {
            assertThat(qualificationResults(fixtures.reset())).isEqualTo(expectedResults);
        }
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

    @Test
    void reLoginReturnsToSelectedConductAndChecksClubAccess() throws Exception {
        var state = fixtures.seed();
        String finished = "/tournaments/team-playoff/admin/" + scenario(state, "finished-8");
        String unfinished = "/tournaments/team-playoff/admin/" + scenario(state, "qualified-8");
        String finishedLogin = "/login?destination=" + URLEncoder.encode(finished, StandardCharsets.UTF_8);
        String unfinishedLogin = "/login?destination=" + URLEncoder.encode(unfinished, StandardCharsets.UTF_8);

        mvc.perform(get(finished)).andExpect(redirectedUrl(finishedLogin));
        mvc.perform(get(unfinished)).andExpect(redirectedUrl(unfinishedLogin));
        mvc.perform(get("/login").param("destination", finished))
                .andExpect(content().string(containsString("value=\"" + finished + "\"")));
        mvc.perform(post("/login").with(csrf()).param("username", "pilot-manager@example.test")
                        .param("password", "wrong-test-password").param("destination", finished))
                .andExpect(redirectedUrl("/login?error=true&destination="
                        + URLEncoder.encode(finished, StandardCharsets.UTF_8)));

        var session = loginManager("pilot-manager@example.test", finished);
        mvc.perform(get(finished).session(session)).andExpect(status().isOk());
        mvc.perform(get("/login").param("destination", unfinished).session(session))
                .andExpect(redirectedUrl(unfinished));
        mvc.perform(get(unfinished).session(session)).andExpect(status().isOk());

        var foreign = loginManager("pilot-foreign@example.test", finished);
        mvc.perform(get(finished).session(foreign)).andExpect(redirectedUrl("/error/403"));
        mvc.perform(get(finished).with(user("player").roles("PLAYER"))).andExpect(redirectedUrl("/error/403"));
    }

    @Test
    void missingFinalAndCorrectionUseTheSameTournamentWithHistory() throws Exception {
        var state = fixtures.seed();
        long event = scenario(state, "qualified-8");
        long secondEvent = scenario(state, "finished-8");
        long otherRevision = history.revision(secondEvent);
        String adminPage = "/tournaments/team-playoff/admin/" + event;
        playoff.initPlayoff(event);
        for (PlayoffStage stage : List.of(PlayoffStage.QUARTER_FINAL, PlayoffStage.SEMI_FINAL)) {
            matches.findByTournamentIdAndPlayoffStage(event, stage)
                    .forEach(match -> playoff.submitPlayoffResult(match.getId(), 6, 3));
        }
        var finalMatch = matches.findByTournamentIdAndPlayoffStage(event, PlayoffStage.FINAL).get(0);
        assertThat(finalMatch.isCompleted()).isFalse();
        var manager = loginManager("pilot-manager@example.test", adminPage);
        mvc.perform(get(adminPage).session(manager)).andExpect(status().isOk());
        String result = "/tournaments/team-playoff/api/playoff-matches/" + finalMatch.getId() + "/result";

        mvc.perform(post(result).session(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"team1Games\":6,\"team2Games\":3}"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"success\":true")));
        assertThat(tournaments.findById(event).orElseThrow().getEstado()).isEqualTo(TournamentStatus.FINALIZADO);
        long savedRevision = history.revision(event);
        long matchCount = matches.findByTournamentIdAndPhase(event, TournamentPhase.PLAYOFF).size();

        var freshSession = loginManager("pilot-manager@example.test", adminPage);
        mvc.perform(get(adminPage).session(freshSession)).andExpect(status().isOk());
        mvc.perform(post(result).session(freshSession).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"team1Games\":6,\"team2Games\":4}"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("\"success\":true")));
        var corrected = matches.findById(finalMatch.getId()).orElseThrow();
        assertThat(corrected.getTeam2Games()).isEqualTo(4);
        assertThat(history.revision(event)).isGreaterThan(savedRevision);
        assertThat(matches.findByTournamentIdAndPhase(event, TournamentPhase.PLAYOFF)).hasSize((int) matchCount);
        assertThat(history.revision(secondEvent)).isEqualTo(otherRevision);
    }

    private MockHttpSession loginManager(String email, String destination) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf()).param("username", email)
                        .param("password", properties.fixturePassword()).param("destination", destination))
                .andExpect(redirectedUrl(destination)).andReturn().getRequest().getSession(false);
    }

    @Test
    void publicQualificationFollowsSavedQueueAndCourtAssignments() throws Exception {
        var state = fixtures.seed();
        var roster = state.scenarios().stream().filter(item -> "roster-8".equals(item.key())).findFirst().orElseThrow();
        long event = roster.tournamentId();
        var teams = new ArrayList<>(roster.teamIds());
        jdbc.sql("UPDATE tournaments_db SET cupo_max=12 WHERE id=:id").param("id", event).update();
        for (int player = 16; player < 24; player += 2) {
            var request = new TeamPlayoffAddTeamRequest();
            request.setPlayer1Id(state.accounts().playerIds().get(player));
            request.setPlayer2Id(state.accounts().playerIds().get(player + 1));
            request.setHasPaid(true);
            request.setAttended(true);
            teams.add(playoff.addTeam(event, request).getId());
        }
        playoff.initQualification(event, 4);
        var firstCourt = playoff.createQualificationMatch(event, teams.get(0), teams.get(1), 1);
        for (int court = 2; court <= 4; court++) {
            int index = 2 * (court - 1);
            playoff.createQualificationMatch(event, teams.get(index), teams.get(index + 1), court);
        }
        assertPublicQueue(publicModel(event), 4);

        var first = playoff.queueQualificationMatch(event, teams.get(10), teams.get(11));
        var second = playoff.queueQualificationMatch(event, teams.get(8), teams.get(9));
        var before = publicModel(event);
        assertPublicQueue(before, 4, first.getId(), second.getId());
        assertTeamState(before, teams.get(10), "QUEUED", 1);
        assertTeamState(before, teams.get(8), "QUEUED", 2);

        playoff.removeFromQueue(first.getId());
        var readded = playoff.queueQualificationMatch(event, teams.get(10), teams.get(11));
        assertPublicQueue(publicModel(event), 4, second.getId(), readded.getId());
        playoff.submitQualResult(firstCourt.getId(), 6, 3);
        playoff.assignCourtToQueuedMatch(second.getId(), 1);
        var after = publicModel(event);
        assertPublicQueue(after, 5, readded.getId());
        assertTeamState(after, teams.get(8), "PLAYING", null);
        assertTeamState(after, teams.get(10), "QUEUED", 1);
        assertThat(publicMatches(after)).filteredOn(match -> match.getId().equals(second.getId()))
                .singleElement().extracting(AmericanoMatchDto::getCourtNumber).isEqualTo(1);
        playoff.removeFromQueue(readded.getId());
        assertPublicQueue(publicModel(event), 5);
    }

    private Map<String, Object> publicModel(long event) throws Exception {
        return mvc.perform(get("/tournaments/team-playoff/" + event)).andExpect(status().isOk())
                .andReturn().getModelAndView().getModel();
    }

    private void assertPublicQueue(Map<String, Object> model, int matchCount, Long... ids) {
        var queue = ((List<?>) model.get("qualificationQueue")).stream().map(AmericanoMatchDto.class::cast).toList();
        assertThat(queue).extracting(AmericanoMatchDto::getId).containsExactly(ids);
        assertThat(queue).allSatisfy(match -> {
            assertThat(match.getCourtNumber()).isNull();
            assertThat(match.getTeam1Player1Name()).isNotBlank();
            assertThat(match.getTeam1Player2Name()).isNotBlank();
            assertThat(match.getTeam2Player1Name()).isNotBlank();
            assertThat(match.getTeam2Player2Name()).isNotBlank();
        });
        var queuedIds = List.of(ids);
        assertThat(publicMatches(model)).hasSize(matchCount).noneMatch(match -> queuedIds.contains(match.getId()));
    }

    private List<AmericanoMatchDto> publicMatches(Map<String, Object> model) {
        return ((List<?>) model.get("qualRounds")).stream().map(AmericanoRoundDto.class::cast)
                .flatMap(round -> round.getMatches().stream()).toList();
    }

    private void assertTeamState(Map<String, Object> model, long teamId, String status, Integer position) {
        var ranking = (TeamAmericanoRankingDto) model.get("ranking");
        assertThat(ranking.getRanking()).filteredOn(team -> team.getId().equals(teamId)).singleElement().satisfies(team -> {
            assertThat(team.getTournamentStatus()).isEqualTo(status);
            assertThat(team.getQueuePosition()).isEqualTo(position);
        });
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
