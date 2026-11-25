package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.dto.SoloRoster.History;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SoloRosterOperations {
    private final JdbcClient jdbc;
    public record Saved(String hash, String result) {}
    public Optional<Saved> find(long tournamentId, UUID requestId) {
        return jdbc.sql("SELECT request_hash, result FROM solo_roster_operations_db WHERE tournament_id=:event AND request_id=:request")
                .param("event", tournamentId).param("request", requestId)
                .query((rs, n) -> new Saved(rs.getString(1), rs.getString(2))).optional();
    }
    public void save(long tournamentId, UUID requestId, long actor, String action, String hash,
                     String result, String before, String after) {
        jdbc.sql("""
                INSERT INTO solo_roster_operations_db
                (request_id,tournament_id,actor_id,action,request_hash,result,before_state,after_state)
                VALUES (:request,:event,:actor,:action,:hash,:result,:before,:after)
                """).param("request", requestId).param("event", tournamentId).param("actor", actor)
                .param("action", action).param("hash", hash).param("result", result)
                .param("before", before).param("after", after).update();
    }
    public List<History> history(long tournamentId) {
        return jdbc.sql("""
                SELECT request_id,action,actor_id,created_at,before_state,after_state
                FROM solo_roster_operations_db WHERE tournament_id=:event ORDER BY created_at DESC LIMIT 100
                """).param("event", tournamentId).query((rs, n) -> new History(rs.getObject(1, UUID.class),
                        rs.getString(2), rs.getLong(3), rs.getTimestamp(4).toLocalDateTime(),
                        rs.getString(5), rs.getString(6))).list();
    }
}
