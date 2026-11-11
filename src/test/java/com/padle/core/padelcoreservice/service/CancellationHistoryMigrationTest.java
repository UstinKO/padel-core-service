package com.padle.core.padelcoreservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CancellationHistoryMigrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    void backfillPreservesKnownTimeDoesNotInventUnknownTimeOrActorAndKeepsActiveRows() {
        String schema = "cancel_migration_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        jdbc.execute("SET LOCAL search_path TO " + schema);
        jdbc.execute("CREATE TABLE tournaments_db(id bigint PRIMARY KEY)");
        jdbc.execute("""
            CREATE TABLE tournament_registrations_db(id bigint PRIMARY KEY,tournament_id bigint,
              player_id bigint,status varchar(24),cancellation_date timestamp,registration_date timestamp,
              pair_booking_id uuid,partner_id bigint)
            """);
        jdbc.execute("INSERT INTO tournaments_db VALUES (1)");
        jdbc.execute("""
            INSERT INTO tournament_registrations_db(id,tournament_id,player_id,status,cancellation_date)
            VALUES (11,1,101,'CANCELLED',null),(12,1,102,'CANCELLED','2026-10-01 12:30:00'),
                   (13,1,103,'CONFIRMED',null)
            """);
        var yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource("db/changelog/versions/v1.71-registration-cancellation-history.yaml"));
        yaml.afterPropertiesSet();
        String sql = json.valueToTree(yaml.getObject()).at("/databaseChangeLog/0/changeSet/changes/0/sql/sql").asText();
        assertThat(sql).contains("CREATE TABLE registration_cancellations");
        var migration = new ResourceDatabasePopulator(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
        jdbc.execute((ConnectionCallback<Boolean>) connection -> { migration.populate(connection); return true; });

        assertThat(jdbc.queryForObject("SELECT cancellation_version FROM tournament_registrations_db WHERE id=11",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cancellation_version FROM tournament_registrations_db WHERE id=13",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration_cancellations",Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration_cancellations WHERE actor_kind='UNKNOWN' AND actor_id IS NULL AND reason_code='LEGACY_UNKNOWN' AND organizer_comment IS NULL",Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration_cancellations e JOIN registration_cancellation_members m ON m.event_id=e.id WHERE m.registration_id=11 AND e.cancelled_at IS NULL",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cancelled_at FROM registration_cancellations e JOIN registration_cancellation_members m ON m.event_id=e.id WHERE m.registration_id=12",LocalDateTime.class)).isEqualTo(LocalDateTime.of(2026,10,1,12,30));
        jdbc.execute("DELETE FROM tournament_registrations_db WHERE id=11");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration_cancellation_members WHERE registration_id=11",Long.class)).isEqualTo(1);
    }
}
