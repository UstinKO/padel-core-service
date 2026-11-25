package com.padle.core.padelcoreservice.preview;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Profile("preview")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayoffPilotFixtureService {
    private final JdbcClient jdbc;
    private final PreviewProperties properties;
    private final PreviewFixtureService preview;
    private final PlayoffPilotData data;
    private final ObjectMapper json;

    public enum Stage { ROSTER, QUALIFIED, FINISHED }
    public record Accounts(long managerId, long foreignManagerId, long clubId, long foreignClubId,
                           List<Long> playerIds) { }
    public record Scenario(String key, int teamCount, Stage stage, long tournamentId, List<Long> teamIds) { }
    public record Fixtures(Accounts accounts, List<Scenario> scenarios) { }

    public Optional<Fixtures> snapshot() {
        return jdbc.sql("SELECT (state->'playoffPilot')::text FROM ui_preview.control WHERE instance_id=:id")
                .param("id", properties.instanceId()).query(String.class).optional()
                .map(value -> json.readValue(value, Fixtures.class));
    }

    @Transactional
    public Fixtures seed() {
        lock();
        return snapshot().orElseGet(() -> save(data.accounts()));
    }

    @Transactional
    public Fixtures reset() {
        lock();
        return snapshot().map(previous -> {
            previous.scenarios().forEach(scenario -> preview.removeEvent(scenario.tournamentId()));
            return save(previous.accounts());
        }).orElseGet(() -> save(data.accounts()));
    }

    private void lock() {
        // Действующая проверка имени БД и UUID выполняется до любой записи пилота.
        preview.seed();
        jdbc.sql("SELECT instance_id FROM ui_preview.control WHERE instance_id=:id FOR UPDATE")
                .param("id", properties.instanceId()).query(UUID.class).optional()
                .orElseThrow(() -> new IllegalStateException("Маркер preview не совпадает"));
    }

    private Fixtures save(Accounts accounts) {
        var fixtures = new Fixtures(accounts, data.scenarios(accounts));
        jdbc.sql("UPDATE ui_preview.control SET state=jsonb_set(state,'{playoffPilot}',CAST(:state AS jsonb)) WHERE instance_id=:id")
                .param("state", json.writeValueAsString(fixtures)).param("id", properties.instanceId()).update();
        return fixtures;
    }
}
