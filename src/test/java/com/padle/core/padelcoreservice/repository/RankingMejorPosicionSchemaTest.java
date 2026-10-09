package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.Ranking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-402: ranking_db.mejor_posicion — NULL означает «позиции ещё нет», колонка nullable и без
 * DEFAULT 0 (v1.54). Проверка на реальной схеме после всех миграций Liquibase.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key",
})
@Transactional
class RankingMejorPosicionSchemaTest {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private RankingRepository rankingRepository;
    @Autowired private PlayerRepository playerRepository;

    private Long playerId;

    @BeforeEach
    void createPlayer() {
        playerId = jdbc.queryForObject("""
                INSERT INTO player_padel_db (nombre, apellido, email, password_hash, fecha_registro,
                    fecha_actualizacion, email_confirmado, activo, nivel_jugador, preferred_locale)
                VALUES ('Test', 'LFPT402', ?, 'x', now(), now(), true, true, 'C9', 'es')
                RETURNING id
                """, Long.class, "lfpt402-" + UUID.randomUUID() + "@example.com");
    }

    @Test
    void columna_esNullableYSinDefault() {
        Map<String, Object> column = jdbc.queryForMap("""
                SELECT is_nullable, column_default FROM information_schema.columns
                WHERE table_name = 'ranking_db' AND column_name = 'mejor_posicion'
                """);

        assertThat(column.get("is_nullable")).isEqualTo("YES");
        assertThat(column.get("column_default")).isNull();
    }

    @Test
    void insertSinMejorPosicion_quedaNull_noCero() {
        jdbc.update("INSERT INTO ranking_db (player_id) VALUES (?)", playerId);

        Integer mejor = jdbc.queryForObject(
                "SELECT mejor_posicion FROM ranking_db WHERE player_id = ?", Integer.class, playerId);
        assertThat(mejor).isNull();
    }

    @Test
    void rankingNuevo_seGuardaSinPosicion_yLaPrimeraPosicionPasaASerLaMejor() {
        Ranking ranking = rankingRepository.saveAndFlush(Ranking.builder()
                .player(playerRepository.findById(playerId).orElseThrow())
                .puntos(1000).torneosJugados(0).torneosGanados(0)
                .partidosGanados(0).partidosPerdidos(0).setsGanados(0).setsPerdidos(0)
                .nivelActual("C9").rachasActual(0).rachasMaxima(0)
                .build());
        assertThat(ranking.getMejorPosicion()).isNull();

        ranking.setPosicionActual(3);
        rankingRepository.saveAndFlush(ranking);
        assertThat(ranking.getMejorPosicion()).isEqualTo(3);

        ranking.setPosicionActual(5);
        rankingRepository.saveAndFlush(ranking);
        assertThat(ranking.getMejorPosicion()).isEqualTo(3);

        ranking.setPosicionActual(1);
        rankingRepository.saveAndFlush(ranking);
        assertThat(ranking.getMejorPosicion()).isEqualTo(1);
    }
}
