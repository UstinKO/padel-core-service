package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.application.roundrobin.RoundRobinReadPort;
import com.padle.core.application.roundrobin.RoundRobinRankingQuery;
import com.padle.core.application.roundrobin.RoundRobinRankingComposer;
import com.padle.core.application.roundrobin.RoundRobinNotFound;
import com.padle.core.padelcoreservice.adapter.roundrobin.LegacyRoundRobinReadAdapter;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoConfigDto;
import com.padle.core.padelcoreservice.mapper.americano.RoundRobinRankingMapper;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.padelcoreservice.repository.americano.*;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.mail.username=test@example.test", "spring.mail.password=test",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test", "app.scheduling.enabled=false"
})
@Transactional
class RoundRobinApplicationIntegrationTest {
    @Autowired ApplicationContext context;
    @Autowired TeamAmericanoService service;
    @Autowired RoundRobinRankingQuery query;
    @Autowired RoundRobinReadPort port;
    @Autowired LegacyRoundRobinReadAdapter adapter;
    @Autowired RoundRobinRankingMapper mapper;
    @Autowired EntityManager entities;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRepository tournaments;
    @Autowired PlayerRepository players;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired AmericanoTeamRepository teams;
    @Autowired AmericanoRoundRepository rounds;
    @Autowired AmericanoMatchRepository matches;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void singleImportedQueryOpensReadOnlyTransactionAndFacadeKeepsLegacyError() {
        assertThat(context.getBeansOfType(RoundRobinRankingQuery.class)).hasSize(1);
        assertThat(context.getBeansOfType(RoundRobinReadPort.class)).hasSize(1);
        assertThat(AopUtils.isAopProxy(query)).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        try (var observer = new QueryObserver()) {
            assertThatThrownBy(() -> query.ranking(-1377L)).isInstanceOf(RoundRobinNotFound.class);
            assertThatThrownBy(() -> service.getRanking(-1377L)).isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Tournament not found: -1377");
            assertThat(observer.reads).hasSize(2).allSatisfy(frame -> {
                assertThat(frame.active()).isTrue();
                assertThat(frame.readOnly()).isTrue();
                assertThat(frame.entityManager()).isNotNull();
            });
        }
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void initializeFinishAndDirectReadUseTheRealApplicationProxyInsideOuterWrite() {
        var event = fixture();
        var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(entities.getEntityManagerFactory());
        try (var observer = new QueryObserver()) {
            assertThat(service.initialize(event.getId(), config()).getRanking()).hasSize(2);
            var first = teams.findByTournamentId(event.getId()).get(0);
            first.setTotalScore(77);
            assertThat(service.getRanking(event.getId()).getRanking().get(0).getTotalScore()).isEqualTo(77);
            var match = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(event.getId()).get(0);
            service.submitMatchResult(match.getId(), 18, 14);
            assertThat(service.finishTournament(event.getId()).isFinished()).isTrue();
            assertThat(observer.reads).extracting(Frame::method).containsExactly("compose", "ranking", "ranking");
            assertThat(observer.reads).allSatisfy(frame -> {
                assertThat(frame.active()).isTrue();
                assertThat(frame.readOnly()).isFalse();
                assertThat(frame.entityManager()).isSameAs(holder.getEntityManager());
            });
        }
    }

    @Test
    void materializedPortValuesSurviveEntityManagerClearWithoutChangingDto() {
        var event = fixture();
        var initial = service.initialize(event.getId(), config());
        entities.flush();
        entities.clear();
        var values = port.findByTournamentId(event.getId()).orElseThrow();
        entities.clear();
        var result = mapper.toDto(RoundRobinRankingComposer.compose(values));
        assertThat(result).usingRecursiveComparison().isEqualTo(initial);
        assertThatThrownBy(() -> result.getAdditionalMatches().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.getFinalRanking().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(values.rankingTeams()).allSatisfy(team -> {
            assertThat(team.player1().name()).startsWith("Query Player");
            assertThat(team.player2().name()).startsWith("Query Player");
            assertThat(team.player1().level()).isEqualTo("C7");
        });
    }

    @Test
    void repositorySourcesStayOrderedAndLoadedCommandDataAvoidsAnotherTournamentQuery() {
        var event = fixture();
        service.initialize(event.getId(), config());
        var loadedTeams = teams.findByTournamentId(event.getId());
        var loadedRounds = rounds.findByTournamentIdOrderByRoundNumberAsc(event.getId());
        var calls = new ArrayList<String>();
        var beans = List.of(tournaments, teams, rounds, matches);
        MethodInterceptor advice = invocation -> {
            calls.add(invocation.getMethod().getName());
            return invocation.proceed();
        };
        beans.forEach(bean -> ((Advised) bean).addAdvice(0, advice));
        try {
            service.getRanking(event.getId());
            assertThat(calls).containsExactly("findById", "findRankingByTournamentId",
                    "findByTournamentIdOrderByRoundNumberAsc", "findByTournamentIdOrderByRoundIdAscMatchNumberAsc",
                    "findByTournamentId", "findByTournamentIdAndPhase");
            calls.clear();
            event.setNombre("Uncommitted event");
            var values = adapter.fromLoaded(event, loadedTeams, loadedRounds);
            assertThat(calls).containsExactly("findByTournamentIdOrderByRoundIdAscMatchNumberAsc",
                    "findByTournamentId", "findByTournamentIdAndPhase");
            assertThat(query.compose(values).tournamentName()).isEqualTo("Uncommitted event");
        } finally {
            beans.forEach(bean -> ((Advised) bean).removeAdvice(advice));
        }
    }

    private Tournament fixture() {
        var suffix = UUID.randomUUID().toString();
        var club = clubs.save(Club.builder().nombre("Application QA " + suffix).isActive(true).build());
        var event = tournaments.save(Tournament.builder().clubId(club.getId()).nombre("Application QA")
                .fechaInicio(LocalDate.now().plusDays(2)).horaInicio(LocalTime.NOON).generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7).tipo(TournamentType.AMERICANO_TEAMS).modalidad(Modalidad.DOBLES)
                .cupoMax(2).precio(BigDecimal.ZERO).estado(TournamentStatus.CERRADO)
                .contactoOrganizador("qa@example.test").isActive(true).build());
        for (int index = 0; index < 2; index++) {
            var first = player(suffix, index * 2);
            var second = player(suffix, index * 2 + 1);
            for (var player : List.of(first, second)) {
                registrations.save(TournamentRegistration.builder().tournament(event).player(player)
                        .status(RegistrationStatus.CONFIRMED).isDoubleRegistration(true).mainPlayerId(first.getId())
                        .partner(player == first ? second : first).position(index + 1).build());
            }
        }
        entities.flush();
        return event;
    }

    private PlayerPadel player(String suffix, int index) {
        return players.save(PlayerPadel.builder().nombre("Query").apellido("Player " + index)
                .email("rr-application-" + suffix + "-" + index + "@example.test").passwordHash("test")
                .activo(true).emailConfirmado(true).oauth2User(false).preferredLocale("es")
                .nivelJugador(Nivel.C7).playingSide(PlayingSide.EITHER).build());
    }

    private TeamAmericanoConfigDto config() {
        var result = new TeamAmericanoConfigDto();
        result.setScoringMode(RoundRobinScoringMode.POINTS);
        result.setCourts(2);
        result.setPointsPerMatch(32);
        return result;
    }

    private record Frame(String method, boolean active, boolean readOnly, EntityManager entityManager) {}

    private class QueryObserver implements AutoCloseable {
        private final List<Frame> reads = new ArrayList<>();
        private final MethodInterceptor advice = invocation -> {
            var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(entities.getEntityManagerFactory());
            reads.add(new Frame(invocation.getMethod().getName(), TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.isCurrentTransactionReadOnly(), holder.getEntityManager()));
            return invocation.proceed();
        };

        QueryObserver() { ((Advised) query).addAdvice(advice); }
        @Override
        public void close() { ((Advised) query).removeAdvice(advice); }
    }
}
