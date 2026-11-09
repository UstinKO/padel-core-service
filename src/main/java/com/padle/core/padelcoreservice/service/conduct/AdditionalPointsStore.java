package com.padle.core.padelcoreservice.service.conduct;

import com.padle.core.padelcoreservice.dto.AdditionalPointsDto;
import com.padle.core.padelcoreservice.dto.AdditionalPointsDto.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AdditionalPointsStore {

    private static final int LEGACY_PENALTY = -10;

    private final JdbcClient jdbc;

    public ScoreSheet forKing(long kingId) {
        return load(kingId, true);
    }

    public ScoreSheet forAmericano(long tournamentId) {
        return load(tournamentId, false);
    }

    private ScoreSheet load(long id, boolean king) {
        String scope = king ? "king_id = :id" : "tournament_id = :id AND king_id IS NULL";
        var stored = jdbc.sql("""
                SELECT id, player_id, round_number, delta, note, cancelled
                FROM tournament_additional_points WHERE %s
                ORDER BY round_number, id
                """.formatted(scope)).param("id", id).query((rs, row) -> new Entry(
                rs.getObject("id", UUID.class), rs.getLong("player_id"), rs.getInt("round_number"),
                rs.getInt("delta"), rs.getString("note"), rs.getBoolean("cancelled"))).list();
        var entries = new ArrayList<>(stored);
        var storedIds = stored.stream().map(Entry::id).collect(Collectors.toSet());
        var legacy = legacyPlayers(id, king);
        for (long playerId : legacy) {
            UUID key = legacyId(id, king, playerId);
            if (!storedIds.contains(key)) {
                entries.add(new Entry(key, playerId, 0, LEGACY_PENALTY, "", false));
            }
        }
        return new ScoreSheet(List.copyOf(entries), summarize(entries),
                legacy.stream().collect(Collectors.toUnmodifiableSet()));
    }

    private List<Long> legacyPlayers(long id, boolean king) {
        if (!king) {
            return jdbc.sql("""
                    SELECT p.player_id FROM americano_players_db p
                    JOIN tournaments_db t ON t.id = p.tournament_id
                    WHERE p.tournament_id = :id AND t.tipo = 'AMERICANO' AND t.modalidad = 'INDIVIDUAL'
                      AND p.total_score::bigint - p.points_scored = :penalty
                    """).param("id", id).param("penalty", LEGACY_PENALTY).query(Long.class).list();
        }
        return jdbc.sql("""
                WITH match_points AS (
                    SELECT points.player_id, SUM(points.score) AS score
                    FROM king_of_court_round_db r
                    JOIN king_of_court_court_db c ON c.round_id = r.id
                    JOIN king_of_court_match_result_db m ON m.court_id = c.id
                    CROSS JOIN LATERAL (VALUES
                        (m.winner1_id, m.winners_score), (m.winner2_id, m.winners_score),
                        (m.loser1_id, m.losers_score), (m.loser2_id, m.losers_score)
                    ) points(player_id, score)
                    WHERE r.tournament_king_id = :id
                    GROUP BY points.player_id
                )
                SELECT s.player_id FROM king_of_court_player_stats_db s
                LEFT JOIN match_points m ON m.player_id = s.player_id
                WHERE s.tournament_king_id = :id
                  AND s.total_points::bigint - s.bonus_points - COALESCE(m.score, 0) = :penalty
                """).param("id", id).param("penalty", LEGACY_PENALTY).query(Long.class).list();
    }

    private static UUID legacyId(long id, boolean king, long playerId) {
        String key = "late-arrival/" + (king ? "king/" : "americano/") + id + "/" + playerId;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private Map<Long, AdditionalPointsDto> summarize(List<Entry> entries) {
        var stages = new java.util.HashMap<Long, TreeMap<Integer, Long>>();
        for (Entry entry : entries) {
            if (!entry.cancelled()) {
                stages.computeIfAbsent(entry.playerId(), key -> new TreeMap<>())
                        .merge(entry.roundNumber(), (long) entry.delta(), Long::sum);
            }
        }
        return stages.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, item -> {
            var byStage = item.getValue().entrySet().stream()
                    .map(stage -> new Stage(stage.getKey(), stage.getValue())).toList();
            return new AdditionalPointsDto(byStage.stream().mapToLong(Stage::points).sum(), byStage);
        }));
    }

    void save(long tournamentId, Long kingId, Entry entry) {
        jdbc.sql("""
                INSERT INTO tournament_additional_points
                    (id, tournament_id, king_id, player_id, round_number, delta, note, cancelled)
                VALUES (:id, :tournament, :king, :player, :round, :delta, :note, :cancelled)
                ON CONFLICT (id) DO UPDATE SET player_id = EXCLUDED.player_id,
                    round_number = EXCLUDED.round_number, delta = EXCLUDED.delta,
                    note = EXCLUDED.note, cancelled = EXCLUDED.cancelled
                WHERE tournament_additional_points.tournament_id = EXCLUDED.tournament_id
                  AND tournament_additional_points.king_id IS NOT DISTINCT FROM EXCLUDED.king_id
                """).param("id", entry.id()).param("tournament", tournamentId).param("king", kingId)
                .param("player", entry.playerId()).param("round", entry.roundNumber()).param("delta", entry.delta())
                .param("note", entry.note()).param("cancelled", entry.cancelled()).update();
    }

    public record Entry(UUID id, long playerId, int roundNumber, int delta, String note, boolean cancelled) {}

    public record ScoreSheet(List<Entry> entries, Map<Long, AdditionalPointsDto> totals,
                             java.util.Set<Long> legacyPlayers) {
        public AdditionalPointsDto points(long playerId) {
            return totals.getOrDefault(playerId, AdditionalPointsDto.EMPTY);
        }

        public long base(long playerId, int previousTotal) {
            return (long) previousTotal - (legacyPlayers.contains(playerId) ? LEGACY_PENALTY : 0);
        }

        public long total(long playerId, int previousTotal) {
            return base(playerId, previousTotal) + points(playerId).total();
        }
    }
}
