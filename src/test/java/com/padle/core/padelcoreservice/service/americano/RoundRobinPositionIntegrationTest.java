package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.application.roundrobin.RoundRobinPositionCommand;
import com.padle.core.application.roundrobin.RoundRobinPositionPort;
import com.padle.core.padelcoreservice.adapter.roundrobin.LegacyRoundRobinPositionAdapter;
import com.padle.core.padelcoreservice.mapper.americano.RoundRobinPositionMapper;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.aopalliance.intercept.MethodInterceptor;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.mail.username=test@example.test", "spring.mail.password=test",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test", "app.scheduling.enabled=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.padle.core.padelcoreservice.service.americano.RoundRobinPositionIntegrationTest$SqlRecorder"
})
public class RoundRobinPositionIntegrationTest {
    @Autowired RoundRobinPositionCommand command;
    @MockitoSpyBean LegacyRoundRobinPositionAdapter port;
    @MockitoSpyBean RoundRobinPositionMapper mapper;
    @Autowired ApplicationContext application;
    @Autowired AmericanoTeamRepository teams;
    @Autowired TournamentRepository events;
    @Autowired ClubRepository clubs;
    @Autowired PlayerRepository players;
    @Autowired EntityManager entities;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcClient jdbc;
    private final List<Fixture> fixtures = new ArrayList<>();
    private final Map<Long, AmericanoTeam> loaded = new HashMap<>();
    private final List<EntityManager> contexts = new ArrayList<>();
    private final AtomicInteger applied = new AtomicInteger();
    private volatile boolean failSecond;
    private MethodInterceptor teamProbe;

    @BeforeEach
    void observePortAndOriginalManagedTeams() {
        teamProbe = invocation -> {
            Object result = invocation.proceed();
            if (invocation.getMethod().getName().equals("findByTournamentId")) {
                for (var value : (List<?>) result) {
                    var team = (AmericanoTeam) value;
                    loaded.put(team.getId(), team);
                    assertThat(entities.contains(team)).isTrue();
                }
            }
            return result;
        };
        ((Advised) teams).addAdvice(0, teamProbe);
        doAnswer(invocation -> {
            captureWriteContext();
            return invocation.callRealMethod();
        }).when(port).load(any(Long.class));
        doAnswer(invocation -> {
            captureWriteContext();
            return invocation.callRealMethod();
        }).when(port).savePositions(any());
        doAnswer(invocation -> {
            AmericanoTeam team = invocation.getArgument(1);
            assertThat(team).isSameAs(loaded.get(team.getId()));
            assertThat(entities.contains(team)).isTrue();
            if (failSecond && applied.incrementAndGet() == 2) {
                throw new PositionFailure();
            }
            Object result = invocation.callRealMethod();
            if (failSecond) {
                entities.flush();
                assertThat(jdbc.sql("SELECT current_position FROM americano_teams_db WHERE id=:id")
                        .param("id", team.getId()).query(Integer.class).single()).isEqualTo(team.getCurrentPosition());
            }
            return result;
        }).when(mapper).apply(any(), any(AmericanoTeam.class));
    }

    @AfterEach
    void removeOnlyOwnedFixtures() {
        ((Advised) teams).removeAdvice(teamProbe);
        tx(() -> {
            fixtures.forEach(fixture -> {
                jdbc.sql("DELETE FROM tournament_conduct_history WHERE tournament_id=:id").param("id", fixture.id()).update();
                jdbc.sql("DELETE FROM americano_teams_db WHERE tournament_id=:id").param("id", fixture.id()).update();
                events.deleteById(fixture.id());
                entities.flush();
                fixture.people().forEach(players::deleteById);
                clubs.deleteById(fixture.club());
            });
            return true;
        });
    }

    @Test
    void directCommandIsTheOnlyBeanAndOpensAWriteTransactionForEmptyInput() {
        assertThat(application.getBeansOfType(RoundRobinPositionCommand.class)).hasSize(1);
        assertThat(application.getBeansOfType(RoundRobinPositionPort.class).values()).containsExactly(port);
        assertThat(AopUtils.isAopProxy(command)).isTrue();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        command.update(Long.MAX_VALUE);
        assertSameWriteContext();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void saveUsesLoadedEntitiesWithoutExtraSelectsOrPlayerInitialization() {
        var fixture = fixture();
        String fields = businessFields(fixture.id());
        var timestamps = timestamps(fixture.id());
        List<String> sql = SqlRecorder.capture(() -> command.update(fixture.id()));
        assertThat(positions(fixture.id())).containsExactly(2, 1);
        assertThat(businessFields(fixture.id())).isEqualTo(fields);
        assertThat(sql.stream().filter(statement -> statement.startsWith("select "))).hasSize(3);
        assertThat(sql.stream().filter(statement -> statement.contains(" from americano_teams_db "))).hasSize(1);
        assertThat(sql.stream().filter(statement -> statement.contains(" from player_padel_db "))).isEmpty();
        var updated = timestamps(fixture.id());
        IntStream.range(0, timestamps.size()).forEach(index -> assertThat(updated.get(index)).isAfter(timestamps.get(index)));
        assertThat(historyCount(fixture.id())).isZero();
        assertSameWriteContext();
    }

    @Test
    void failureOnSecondEntityRollsBackTheAlreadyFlushedFirstRowAndTimestamp() {
        var fixture = fixture();
        String before = fullRows(fixture.id());
        failSecond = true;
        assertThatThrownBy(() -> command.update(fixture.id())).isInstanceOf(PositionFailure.class);
        assertThat(applied).hasValue(2);
        assertThat(fullRows(fixture.id())).isEqualTo(before);
        assertThat(positions(fixture.id())).containsExactly(9, 9);
        assertThat(historyCount(fixture.id())).isZero();
        assertSameWriteContext();
    }

    private void captureWriteContext() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
        var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(entities.getEntityManagerFactory());
        assertThat(holder).isNotNull();
        contexts.add(holder.getEntityManager());
    }

    private void assertSameWriteContext() {
        assertThat(contexts).hasSize(2);
        assertThat(contexts.get(0)).isSameAs(contexts.get(1));
    }

    private Fixture fixture() {
        var fixture = tx(() -> {
            String suffix = UUID.randomUUID().toString();
            var club = clubs.save(Club.builder().nombre("Position QA " + suffix).isActive(true).build());
            var event = events.save(Tournament.builder().clubId(club.getId()).nombre("Position QA")
                    .fechaInicio(LocalDate.now().plusDays(2)).horaInicio(LocalTime.NOON).generoFormato(GenderFormat.MIXTO)
                    .categoriaNivel(Nivel.C7).tipo(TournamentType.AMERICANO_TEAMS).modalidad(Modalidad.DOBLES)
                    .cupoMax(2).precio(BigDecimal.ZERO).estado(TournamentStatus.CERRADO)
                    .contactoOrganizador("qa@example.test").isActive(true).build());
            var people = IntStream.range(0, 2).mapToObj(index -> {
                var player = players.save(PlayerPadel.builder().nombre("Position").apellido("QA " + index)
                        .email("position-" + suffix + "-" + index + "@example.test").passwordHash("test")
                        .activo(true).emailConfirmado(true).oauth2User(false).preferredLocale("es").nivelJugador(Nivel.C7).build());
                teams.save(AmericanoTeam.builder().tournament(event).player1(player).player2Name("Guest QA")
                        .teamNumber(index + 1).currentPosition(9).totalScore((index + 1) * 10).build());
                return player.getId();
            }).toList();
            entities.flush();
            return new Fixture(event.getId(), club.getId(), people);
        });
        fixtures.add(fixture);
        return fixture;
    }

    private <T> T tx(Supplier<T> work) { return new TransactionTemplate(transactions).execute(status -> work.get()); }
    private List<Integer> positions(long id) {
        return jdbc.sql("SELECT current_position FROM americano_teams_db WHERE tournament_id=:id ORDER BY team_number")
                .param("id", id).query(Integer.class).list();
    }
    private List<LocalDateTime> timestamps(long id) {
        return jdbc.sql("SELECT updated_at FROM americano_teams_db WHERE tournament_id=:id ORDER BY team_number")
                .param("id", id).query(LocalDateTime.class).list();
    }
    private String businessFields(long id) { return rows(id, "to_jsonb(t)-'current_position'-'updated_at'"); }
    private String fullRows(long id) { return rows(id, "to_jsonb(t)"); }
    private String rows(long id, String expression) {
        return jdbc.sql("SELECT md5(string_agg((" + expression + ")::text,'|' ORDER BY id)) FROM americano_teams_db t WHERE tournament_id=:id")
                .param("id", id).query(String.class).single();
    }
    private long historyCount(long id) {
        return jdbc.sql("SELECT count(*) FROM tournament_conduct_history WHERE tournament_id=:id")
                .param("id", id).query(Long.class).single();
    }
    private record Fixture(long id, long club, List<Long> people) {}
    private static final class PositionFailure extends RuntimeException {}

    public static final class SqlRecorder implements StatementInspector {
        private static final ThreadLocal<List<String>> ACTIVE = new ThreadLocal<>();
        static List<String> capture(Runnable work) {
            var sql = new ArrayList<String>();
            ACTIVE.set(sql);
            try { work.run(); return List.copyOf(sql); }
            finally { ACTIVE.remove(); }
        }
        @Override public String inspect(String sql) {
            var current = ACTIVE.get();
            if (current != null) { current.add(sql.toLowerCase(Locale.ROOT)); }
            return sql;
        }
    }
}
