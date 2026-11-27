package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.RoundRobinAdditionalMatchRequest;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoConfigDto;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.application.identity.PlayingSide;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.aopalliance.intercept.MethodInterceptor;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.test", "spring.mail.password=test",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test", "app.scheduling.enabled=false"
})
@Transactional
class RoundRobinRankingBaselineTest {
    private final List<String> missingSnapshots = new ArrayList<>();
    @Autowired TeamAmericanoService service;
    @Autowired RoundRobinAdditionalMatches additional;
    @Autowired TournamentRepository tournaments;
    @Autowired ClubRepository clubs;
    @Autowired PlayerRepository players;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired AmericanoTeamRepository teams;
    @Autowired AmericanoRoundRepository rounds;
    @Autowired AmericanoMatchRepository matches;
    @Autowired EntityManager entities;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcClient jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @AfterEach
    void allWireSnapshotsMustExist() {
        assertThat(missingSnapshots).as("Отсутствующие wire-эталоны").isEmpty();
    }

    @ParameterizedTest
    @EnumSource(RoundRobinScoringMode.class)
    void fullWireLifecycleRetainsNullFieldsAndFinishedSemantics(RoundRobinScoringMode mode) throws Exception {
        var fixture = fixture(2);
        initialize(fixture, mode);
        assertWire(mode.name().toLowerCase() + "-initial", fixture);
        scoreCircle(fixture, mode);
        var completed = wire(fixture.id());
        assertThat(completed.path("roundRobinComplete").asBoolean()).isTrue();
        assertThat(completed.path("finished").asBoolean()).isTrue();
        assertThat(tournaments.findById(fixture.id()).orElseThrow().getEstado()).isEqualTo(TournamentStatus.CERRADO);
        assertWire(mode.name().toLowerCase() + "-circle", fixture);
        var pair = teams.findByTournamentId(fixture.id()).stream()
                .sorted(Comparator.comparing(team -> team.getTeamNumber())).toList();
        var extra = additional.add(fixture.id(), new RoundRobinAdditionalMatchRequest(
                pair.get(0).getId(), pair.get(1).getId(), 1, 1, "Final QA", UUID.randomUUID()));
        assertThat(wire(fixture.id()).path("finished").asBoolean()).isFalse();
        assertWire(mode.name().toLowerCase() + "-pending-final", fixture);
        service.submitMatchResult(extra.getId(), mode == RoundRobinScoringMode.SETS ? 4 : 14,
                mode == RoundRobinScoringMode.SETS ? 6 : 18);
        var beforeFinish = wire(fixture.id());
        assertWire(mode.name().toLowerCase() + "-played-final", fixture);
        var finished = service.finishTournament(fixture.id());
        JsonNode finishedWire = json.readTree(json.writeValueAsString(finished));
        assertThat(finishedWire).isEqualTo(beforeFinish);
        assertThat(wire(fixture.id())).isEqualTo(beforeFinish);
        assertThat(service.getTeams(fixture.id())).usingRecursiveComparison().isEqualTo(finished.getRanking());
    }

    @Test
    void emptyUninitializedTournamentKeepsDefaultConfiguration() throws Exception {
        var fixture = fixture(0);
        assertWire("empty", fixture);
        assertThat(service.isInitialized(fixture.id())).isFalse();
    }

    @Test
    void guestLevelsAndPlayingSidesHaveDifferentJavaAndWireVisibility() throws Exception {
        var fixture = fixture(2);
        initialize(fixture, RoundRobinScoringMode.POINTS);
        var pair = teams.findByTournamentId(fixture.id()).stream()
                .filter(team -> team.getTeamNumber() == 1).findFirst().orElseThrow();
        pair.getPlayer1().setNivelJugador(Nivel.SIN_ESPECIFICAR);
        pair.getPlayer1().setPlayingSide(PlayingSide.LEFT);
        pair.getPlayer1().setTelefono("+5491100000088");
        pair.setPlayer2(null);
        pair.setPlayer2Name("Invitado QA");
        pair.setPlayer2Phone("+5491100000099");
        pair.setHasPaid(true);
        pair.setAttended(true);
        pair.setAdminComment("No copiar al ranking");
        var secondPair = teams.findByTournamentId(fixture.id()).stream()
                .filter(team -> team.getTeamNumber() == 2).findFirst().orElseThrow();
        secondPair.getPlayer2().setTelefono("+5491100000077");
        secondPair.getPlayer2().setPlayingSide(PlayingSide.RIGHT);
        var dto = service.getRanking(fixture.id()).getRanking().get(0);
        assertThat(dto.getPlayer1PlayingSide()).isEqualTo(PlayingSide.LEFT);
        assertThat(dto.getPlayer2PlayingSide()).isNull();
        assertThat(dto.getPlayer1Nivel()).isNull();
        assertThat(dto.getPlayer2Nivel()).isNull();
        assertThat(dto.getHasPaid()).isNull();
        assertThat(dto.getAttended()).isNull();
        assertThat(dto.getAdminComment()).isNull();
        assertThat(service.getRanking(fixture.id()).getRanking().get(1).getPlayer2PlayingSide()).isEqualTo(PlayingSide.RIGHT);
        assertThat(wire(fixture.id()).path("ranking").get(0).has("player1PlayingSide")).isFalse();
        assertWire("guest", fixture);
    }

    @Test
    void pointsDrawKeepsNonzeroDrawCountersAndSequentialPlaces() throws Exception {
        var fixture = fixture(2);
        initialize(fixture, RoundRobinScoringMode.POINTS);
        var match = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(fixture.id()).get(0);
        service.submitMatchResult(match.getId(), 16, 16);
        var ranking = service.getRanking(fixture.id()).getRanking();
        assertThat(ranking).extracting(row -> row.getMatchesDrawn()).containsExactly(1, 1);
        assertThat(ranking).extracting(row -> row.getMatchesWon()).containsExactly(0, 0);
        assertThat(ranking).extracting(row -> row.getCurrentPosition()).containsExactly(1, 2);
        assertWire("points-draw", fixture);
    }

    @Test
    void partialRoundAndLegacyNullModeKeepCurrentFallbacks() throws Exception {
        var fixture = fixture(4);
        initialize(fixture, RoundRobinScoringMode.POINTS);
        rounds.findByTournamentIdOrderByRoundNumberAsc(fixture.id()).forEach(round -> round.setScoringMode(null));
        var match = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(fixture.id()).get(0);
        service.submitMatchResult(match.getId(), 18, 14);
        var result = wire(fixture.id());
        assertThat(result.path("scoringMode").asString()).isEqualTo("POINTS");
        assertThat(result.path("totalRounds").asInt()).isEqualTo(3);
        assertThat(result.path("completedRounds").asInt()).isZero();
        assertThat(result.path("roundRobinComplete").asBoolean()).isFalse();
        assertThat(result.path("finished").asBoolean()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(RoundRobinScoringMode.class)
    void otherPlacementAndUnplacedMatchLeaveCircleStatisticsIntact(RoundRobinScoringMode mode) throws Exception {
        var fixture = fixture(5);
        initialize(fixture, mode);
        scoreCircle(fixture, mode);
        var circle = service.getRanking(fixture.id()).getRanking();
        var placed = additional.add(fixture.id(), new RoundRobinAdditionalMatchRequest(
                circle.get(3).getId(), circle.get(4).getId(), 1, 4, "  Other QA  ", UUID.randomUUID()));
        service.submitMatchResult(placed.getId(), mode == RoundRobinScoringMode.SETS ? 4 : 14,
                mode == RoundRobinScoringMode.SETS ? 6 : 18);
        var free = additional.add(fixture.id(), new RoundRobinAdditionalMatchRequest(
                circle.get(0).getId(), circle.get(1).getId(), 1, null, null, UUID.randomUUID()));
        var pending = service.getRanking(fixture.id());
        assertThat(pending.getFinalRanking().get(3).teamId()).isEqualTo(circle.get(4).getId());
        assertThat(pending.getFinalRanking().get(4).teamId()).isEqualTo(circle.get(3).getId());
        assertThat(pending.getRanking()).usingRecursiveComparison().isEqualTo(circle);
        assertThat(pending.getAdditionalMatches()).filteredOn(match -> match.id().equals(free.getId()))
                .singleElement().satisfies(match -> {
                    assertThat(match.placementStart()).isNull();
                    assertThat(match.label()).isEmpty();
                });
        var result = wire(fixture.id());
        assertThat(result.path("finished").asBoolean()).isFalse();
        assertThat(result.path("additionalMatches").size()).isEqualTo(2);
    }

    @Test
    void publicViewAndAuthenticatedApiKeepSeparateAccessRules() throws Exception {
        var fixture = fixture(2);
        initialize(fixture, RoundRobinScoringMode.SETS);
        var own = owner(70001L, fixture.clubId());
        var foreign = owner(70002L, fixture.clubId());
        tournaments.findById(fixture.id()).orElseThrow().setOwnerId(own.getId());
        mvc.perform(get(api(fixture.id())).accept("application/json")).andExpect(status().isUnauthorized());
        mvc.perform(get(api(fixture.id())).accept("text/html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/tournaments/team-americano/" + fixture.id())).andExpect(status().isOk());
        mvc.perform(get(api(fixture.id())).with(user(foreign))).andExpect(status().isOk());
        mvc.perform(get("/tournaments/team-americano/admin/" + fixture.id()).with(user(own)))
                .andExpect(status().isOk());
        var denied = mvc.perform(get("/tournaments/team-americano/admin/" + fixture.id()).with(user(foreign)))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        assertThat(denied.getRedirectedUrl()).isEqualTo("/error/403");
        mvc.perform(get("/tournaments/team-americano/admin/" + fixture.id()).with(user(foreign))
                .accept("application/json")).andExpect(status().isForbidden());
        var redirect = mvc.perform(get("/tournaments/team-americano/" + fixture.id() + "/ranking"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse();
        assertThat(redirect.getRedirectedUrl()).isEqualTo("/tournaments/team-americano/" + fixture.id());
    }

    @Test
    void missingTournamentKeepsServiceErrorAndUnresolvedMvcException() {
        assertThatThrownBy(() -> service.getRanking(-1375L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Tournament not found: -1375");
        assertThatThrownBy(() -> mvc.perform(get(api(-1375L)).with(user("rr-reader").roles("PLAYER"))
                .accept("application/json")))
                .isInstanceOf(ServletException.class).hasCauseInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void externalGetIsReadOnlyUsesOneTransactionAndDoesNotPersistComputedPositions() throws Exception {
        var fixture = new TransactionTemplate(transactions).execute(status -> {
            var value = fixture(2);
            initialize(value, RoundRobinScoringMode.POINTS);
            teams.findByTournamentId(value.id()).forEach(team -> team.setCurrentPosition(9));
            return value;
        });
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        var before = stored(fixture.id());
        var statistics = entities.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try (var observer = new RepositoryObserver(tournaments, teams, rounds, matches)) {
            assertThat(service.getRanking(fixture.id()).getRanking()).extracting(row -> row.getCurrentPosition())
                    .containsExactly(1, 2);
            var firstReads = List.copyOf(observer.reads);
            assertThat(firstReads).isNotEmpty().allSatisfy(read -> {
                assertThat(read.active()).isTrue();
                assertThat(read.readOnly()).isTrue();
                assertThat(read.entityManager()).isSameAs(firstReads.get(0).entityManager());
            });
            observer.reads.clear();
            wire(fixture.id());
            assertThat(observer.reads).isNotEmpty().allSatisfy(read -> {
                assertThat(read.active()).isTrue();
                assertThat(read.readOnly()).isTrue();
                assertThat(read.entityManager()).isSameAs(observer.reads.get(0).entityManager());
            });
            assertThat(statistics.getEntityInsertCount()).isZero();
            assertThat(statistics.getEntityUpdateCount()).isZero();
            assertThat(statistics.getEntityDeleteCount()).isZero();
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(stored(fixture.id())).isEqualTo(before);
        assertThat(jdbc.sql("SELECT count(*) FROM americano_teams_db WHERE tournament_id=:id AND current_position=9")
                .param("id", fixture.id()).query(Long.class).single()).isEqualTo(2);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.sql("DELETE FROM marketing_events_db WHERE tournament_id=:id").param("id", fixture.id()).update();
            jdbc.sql("""
                    DELETE FROM marketing_payment_progress_db WHERE payment_id IN
                    (SELECT p.id FROM payments_db p JOIN tournament_registrations_db r
                    ON r.id=p.registration_id WHERE r.tournament_id=:id)
                    """).param("id", fixture.id()).update();
            for (var table : List.of("marketing_events_db", "marketing_paid_slots_db", "marketing_registrations_db", "payments_db")) {
                jdbc.sql("DELETE FROM " + table + " WHERE registration_id IN "
                        + "(SELECT id FROM tournament_registrations_db WHERE tournament_id=:id)")
                        .param("id", fixture.id()).update();
            }
            jdbc.sql("DELETE FROM tournament_conduct_history WHERE tournament_id=:id").param("id", fixture.id()).update();
            registrations.deleteAll(registrations.findByTournamentIdOrderByPositionAscWaitlistPositionAsc(fixture.id()));
            matches.deleteAll(matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(fixture.id()));
            rounds.deleteAll(rounds.findByTournamentIdOrderByRoundNumberAsc(fixture.id()));
            teams.deleteAll(teams.findByTournamentId(fixture.id()));
            tournaments.deleteById(fixture.id());
            players.deleteAllById(fixture.playerIds());
            clubs.deleteById(fixture.clubId());
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void initializeFinishAndReadJoinOuterWriteAndRollbackHistoryTogether() {
        var transaction = new TransactionTemplate(transactions);
        var ids = new ArrayList<Long>();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            var fixture = fixture(2);
            ids.add(fixture.id());
            var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(entities.getEntityManagerFactory());
            try (var observer = new RepositoryObserver(tournaments, teams, rounds, matches)) {
                var initial = service.initialize(fixture.id(), config(RoundRobinScoringMode.POINTS));
                assertThat(initial.getRanking()).hasSize(2);
                var first = teams.findByTournamentId(fixture.id()).get(0);
                first.setTotalScore(77);
                assertThat(service.getRanking(fixture.id()).getRanking().get(0).getTotalScore()).isEqualTo(77);
                scoreCircle(fixture, RoundRobinScoringMode.POINTS);
                assertThat(service.finishTournament(fixture.id()).isFinished()).isTrue();
                assertThat(historyCount(fixture.id())).isPositive();
                assertThat(observer.reads).isNotEmpty().allSatisfy(read -> {
                    assertThat(read.active()).isTrue();
                    assertThat(read.readOnly()).isFalse();
                    assertThat(read.entityManager()).isSameAs(holder.getEntityManager());
                });
                throw new RollbackProbe();
            }
        })).isInstanceOf(RollbackProbe.class);
        assertThat(tournaments.existsById(ids.get(0))).isFalse();
        assertThat(teams.findByTournamentId(ids.get(0))).isEmpty();
        assertThat(rounds.findByTournamentIdOrderByRoundNumberAsc(ids.get(0))).isEmpty();
        assertThat(historyCount(ids.get(0))).isZero();
    }

    private void assertWire(String name, Fixture fixture) throws Exception {
        var actual = normalized(wire(fixture.id()), fixture);
        var output = Path.of("target/round-robin-query-baseline/" + name + ".json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(actual) + "\n");
        var resource = getClass().getResourceAsStream("/contracts/round-robin-query/" + name + ".json");
        if (resource == null) {
            missingSnapshots.add(name);
            return;
        }
        try (resource) {
            assertThat(actual).isEqualTo(json.readTree(resource));
        }
    }

    private JsonNode wire(Long id) throws Exception {
        var response = mvc.perform(get(api(id)).with(user("rr-reader").roles("PLAYER")).accept("application/json"))
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }

    private JsonNode normalized(JsonNode source, Fixture fixture) {
        var result = (ObjectNode) source.deepCopy();
        replaceId(result, "tournamentId", Map.of(fixture.id(), 1L));
        var teamIds = teams.findByTournamentId(fixture.id()).stream()
                .collect(Collectors.toMap(team -> team.getId(), team -> 100L + team.getTeamNumber()));
        var playerIds = IntStream.range(0, fixture.playerIds().size()).boxed()
                .collect(Collectors.toMap(fixture.playerIds()::get, index -> 200L + index));
        for (var row : result.path("ranking")) {
            var team = (ObjectNode) row;
            replaceId(team, "id", teamIds);
            replaceId(team, "tournamentId", Map.of(fixture.id(), 1L));
            replaceId(team, "player1Id", playerIds);
            replaceId(team, "player2Id", playerIds);
            for (var field : List.of("player1Email", "player2Email")) {
                if (team.path(field).isString()) {
                    team.put(field, team.path(field).asString().replace(fixture.suffix(), "fixture"));
                }
            }
        }
        for (var row : result.path("finalRanking")) {
            replaceId((ObjectNode) row, "teamId", teamIds);
        }
        var extraIds = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(fixture.id()).stream()
                .filter(match -> match.getRound().getPhase() != null).map(match -> match.getId()).toList();
        var extras = IntStream.range(0, extraIds.size()).boxed()
                .collect(Collectors.toMap(extraIds::get, index -> 300L + index));
        for (var row : result.path("additionalMatches")) {
            replaceId((ObjectNode) row, "id", extras);
            replaceId((ObjectNode) row, "team1Id", teamIds);
            replaceId((ObjectNode) row, "team2Id", teamIds);
        }
        return json.readTree(json.writeValueAsString(result));
    }

    private void replaceId(ObjectNode node, String field, Map<Long, Long> ids) {
        if (node.path(field).isIntegralNumber()) {
            long original = node.path(field).asLong();
            node.put(field, ids.getOrDefault(original, original));
        }
    }

    private Fixture fixture(int count) {
        var suffix = UUID.randomUUID().toString();
        var club = clubs.save(Club.builder().nombre("RR Query QA " + suffix).isActive(true).build());
        var tournament = tournaments.save(Tournament.builder().clubId(club.getId()).nombre("RR Query QA")
                .fechaInicio(LocalDate.now().plusDays(2)).horaInicio(LocalTime.NOON).generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7).tipo(TournamentType.AMERICANO_TEAMS)
                .modalidad(Modalidad.DOBLES).cupoMax(Math.max(2, count)).precio(BigDecimal.ZERO)
                .estado(TournamentStatus.CERRADO).contactoOrganizador("qa@example.test").isActive(true).build());
        var ids = new ArrayList<Long>();
        for (int index = 0; index < count; index++) {
            var first = player(suffix, index * 2);
            var second = player(suffix, index * 2 + 1);
            ids.add(first.getId());
            ids.add(second.getId());
            for (var player : List.of(first, second)) {
                registrations.save(TournamentRegistration.builder().tournament(tournament).player(player)
                        .status(RegistrationStatus.CONFIRMED).isDoubleRegistration(true).mainPlayerId(first.getId())
                        .partner(player == first ? second : first).position(index + 1).build());
            }
        }
        entities.flush();
        return new Fixture(tournament.getId(), club.getId(), ids, suffix);
    }

    private PlayerPadel player(String suffix, int index) {
        return players.save(PlayerPadel.builder().nombre("Contract").apellido("Player " + index)
                .email("rr-query-" + suffix + "-" + index + "@example.test").passwordHash("test")
                .activo(true).emailConfirmado(true).oauth2User(false).preferredLocale("es")
                .nivelJugador(Nivel.C7).playingSide(PlayingSide.EITHER).build());
    }

    private void initialize(Fixture fixture, RoundRobinScoringMode mode) {
        service.initialize(fixture.id(), config(mode));
        teams.findByTournamentId(fixture.id()).forEach(team ->
                team.setTeamNumber(fixture.playerIds().indexOf(team.getPlayer1().getId()) / 2 + 1));
        service.updateTeamPositions(fixture.id());
    }

    private TeamAmericanoConfigDto config(RoundRobinScoringMode mode) {
        var config = new TeamAmericanoConfigDto();
        config.setScoringMode(mode);
        config.setCourts(2);
        config.setPointsPerMatch(32);
        return config;
    }

    private void scoreCircle(Fixture fixture, RoundRobinScoringMode mode) {
        var byId = teams.findByTournamentId(fixture.id()).stream()
                .collect(Collectors.toMap(team -> team.getId(), team -> team.getTeamNumber()));
        matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(fixture.id()).stream()
                .filter(match -> match.getRound().getPhase() == null).forEach(match -> {
                    int winner = mode == RoundRobinScoringMode.SETS ? 6 : 18;
                    int loser = mode == RoundRobinScoringMode.SETS ? 4 : 14;
                    boolean first = byId.get(match.getTeam1Id()) < byId.get(match.getTeam2Id());
                    service.submitMatchResult(match.getId(), first ? winner : loser, first ? loser : winner);
                });
    }

    private long historyCount(Long id) {
        return jdbc.sql("SELECT count(*) FROM tournament_conduct_history WHERE tournament_id=:id")
                .param("id", id).query(Long.class).single();
    }

    private Stored stored(Long id) {
        return new Stored(rowDigest("americano_teams_db", id), rowDigest("americano_rounds_db", id),
                rowDigest("americano_matches_db", id), historyCount(id));
    }

    private String rowDigest(String table, Long id) {
        return jdbc.sql("SELECT md5(coalesce(string_agg(to_jsonb(t)::text,',' ORDER BY id),'')) FROM "
                + table + " t WHERE tournament_id=:id").param("id", id).query(String.class).single();
    }

    private static Owner owner(Long id, Long clubId) {
        return Owner.builder().id(id).email("owner-" + id + "@example.test").firstName("QA")
                .lastName("Owner").role(OwnerRole.CLUB_ADMIN).clubId(clubId).build();
    }

    private static String api(Long id) {
        return "/tournaments/team-americano/api/" + id + "/ranking";
    }

    private record Fixture(Long id, Long clubId, List<Long> playerIds, String suffix) {}
    private record Stored(String teams, String rounds, String matches, long history) {}
    private record ReadObservation(boolean active, boolean readOnly, EntityManager entityManager) {}
    private static final class RollbackProbe extends RuntimeException {}

    private final class RepositoryObserver implements AutoCloseable {
        private final List<ReadObservation> reads = new ArrayList<>();
        private final List<Advised> proxies;
        private final MethodInterceptor interceptor = invocation -> {
            if (invocation.getMethod().getName().startsWith("find")) {
                var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(entities.getEntityManagerFactory());
                reads.add(new ReadObservation(TransactionSynchronizationManager.isActualTransactionActive(),
                        TransactionSynchronizationManager.isCurrentTransactionReadOnly(), holder.getEntityManager()));
            }
            return invocation.proceed();
        };

        RepositoryObserver(Object... repositories) {
            proxies = Arrays.stream(repositories).map(repository -> (Advised) repository).toList();
            proxies.forEach(proxy -> proxy.addAdvice(0, interceptor));
        }

        @Override
        public void close() {
            proxies.forEach(proxy -> proxy.removeAdvice(interceptor));
        }
    }
}
