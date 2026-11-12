package com.padle.core.padelcoreservice.service.conduct;

import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.service.conduct.ConductChange.Subject;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/** Serializes only the relational tables that describe the running competition. */
@Repository
@RequiredArgsConstructor
class ConductSnapshotStore {

    private static final String KING = "tournament_king_of_court_db";
    private static final String KING_ROUND = "king_of_court_round_db";
    private static final String KING_COURT = "king_of_court_court_db";
    private static final String KING_RESULT = "king_of_court_match_result_db";
    private static final String KING_SCOPE = "SELECT id FROM " + KING + " WHERE tournament_id = :id";
    private static final String ROUND_SCOPE = "SELECT id FROM " + KING_ROUND + " WHERE tournament_king_id IN (" + KING_SCOPE + ")";
    private static final String COURT_SCOPE = "SELECT id FROM " + KING_COURT + " WHERE round_id IN (" + ROUND_SCOPE + ")";
    private static final String TEAM = "americano_teams_db";

    // Parent tables precede children. Names and predicates are constants, never request parameters.
    private static final SnapshotTable POINTS = new SnapshotTable("tournament_additional_points", "tournament_id = :id");
    private static final List<SnapshotTable> KING_TABLES = List.of(
            new SnapshotTable(KING, "tournament_id = :id", "id", "youtube_link"),
            new SnapshotTable(KING_ROUND, "tournament_king_id IN (" + KING_SCOPE + ")"),
            new SnapshotTable(KING_COURT, "round_id IN (" + ROUND_SCOPE + ")"),
            new SnapshotTable("court_players_db", "court_id IN (" + COURT_SCOPE + ")", "court_id, player_id"),
            new SnapshotTable("court_teams_db", "court_id IN (" + COURT_SCOPE + ")"),
            new SnapshotTable(KING_RESULT, "court_id IN (" + COURT_SCOPE + ")"),
            new SnapshotTable("king_of_court_player_stats_db", "tournament_king_id IN (" + KING_SCOPE + ")"), POINTS);
    private static final List<SnapshotTable> AMERICANO_TABLES = List.of(
            new SnapshotTable("americano_players_db", "tournament_id = :id"),
            new SnapshotTable(TEAM, "tournament_id = :id", "id", "has_paid", "admin_comment", "player2_phone"),
            new SnapshotTable("americano_rounds_db", "tournament_id = :id"),
            new SnapshotTable("americano_matches_db", "tournament_id = :id"));

    private final JdbcClient jdbc;

    TournamentType lockTournament(long tournamentId) {
        return jdbc.sql("SELECT tipo FROM tournaments_db WHERE id = :id FOR UPDATE")
                .param("id", tournamentId).query(TournamentType.class).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));
    }

    Target target(Subject subject, long id) {
        if (subject == Subject.TOURNAMENT) {
            return new Target(id, null, null, null);
        }
        String query = switch (subject) {
            case KING -> """
                    SELECT tournament_id, current_round AS round_number, NULL::integer AS court_number, NULL AS stage
                    FROM tournament_king_of_court_db WHERE id = :id
                    """;
            case KING_ROUND -> """
                    SELECT k.tournament_id, r.round_number, NULL::integer AS court_number, NULL AS stage
                    FROM king_of_court_round_db r JOIN tournament_king_of_court_db k ON k.id = r.tournament_king_id
                    WHERE r.id = :id
                    """;
            case KING_RESULT -> """
                    SELECT k.tournament_id, r.round_number, c.court_number, NULL AS stage
                    FROM king_of_court_match_result_db m JOIN king_of_court_court_db c ON c.id = m.court_id
                    JOIN king_of_court_round_db r ON r.id = c.round_id
                    JOIN tournament_king_of_court_db k ON k.id = r.tournament_king_id WHERE m.id = :id
                    """;
            case AMERICANO_ROUND -> """
                    SELECT tournament_id, round_number, NULL::integer AS court_number, phase AS stage
                    FROM americano_rounds_db WHERE id = :id
                    """;
            case AMERICANO_MATCH, AMERICANO_RESULT -> """
                    SELECT m.tournament_id, r.round_number, m.court_number,
                           COALESCE(m.playoff_stage, r.phase) AS stage
                    FROM americano_matches_db m JOIN americano_rounds_db r ON r.id = m.round_id WHERE m.id = :id
                    """;
            case AMERICANO_TEAM -> """
                    SELECT tournament_id, NULL::integer AS round_number, NULL::integer AS court_number, NULL AS stage
                    FROM americano_teams_db WHERE id = :id
                    """;
            default -> throw new IllegalArgumentException("Unsupported conduct subject");
        };
        return jdbc.sql(query).param("id", id).query((rs, n) -> new Target(
                rs.getLong("tournament_id"), rs.getObject("round_number", Integer.class),
                rs.getObject("court_number", Integer.class), rs.getString("stage"))).optional()
                .orElseThrow(() -> new ResourceNotFoundException("Tournament action target not found"));
    }

    Target position(long tournamentId, TournamentType type) {
        if (type == TournamentType.KING_OF_COURT) {
            return jdbc.sql("""
                    SELECT current_round FROM tournament_king_of_court_db WHERE tournament_id = :id
                    ORDER BY is_active DESC, id DESC LIMIT 1
                    """).param("id", tournamentId).query(Integer.class).optional()
                    .map(round -> new Target(tournamentId, round, null, null))
                    .orElse(new Target(tournamentId, null, null, null));
        }
        return jdbc.sql("""
                SELECT round_number, phase FROM americano_rounds_db
                WHERE tournament_id = :id
                ORDER BY (phase IS NOT DISTINCT FROM 'PLAYOFF') DESC,
                         (status IS NOT DISTINCT FROM 'IN_PROGRESS') DESC, round_number DESC LIMIT 1
                """).param("id", tournamentId)
                .query((rs, n) -> new Target(tournamentId, rs.getInt("round_number"), null, rs.getString("phase")))
                .optional().orElse(new Target(tournamentId, null, null, null));
    }

    String capture(long tournamentId, TournamentType type) {
        String fields = tables(type).stream().map(SnapshotTable::snapshotExpression)
                .collect(Collectors.joining(", "));
        return jdbc.sql("""
                SELECT CAST(jsonb_build_object('status', estado, 'americanoFinalOrder', americano_final_order, %s) AS text)
                FROM tournaments_db WHERE id = :id
                """.formatted(fields)).param("id", tournamentId).query(String.class).single();
    }

    String registrations(long tournamentId) {
        return jdbc.sql("""
                SELECT CAST(COALESCE(jsonb_agg(jsonb_build_array(id, player_id, status, partner_id,
                       main_player_id, is_active) ORDER BY id), '[]'::jsonb) AS text)
                FROM tournament_registrations_db WHERE tournament_id = :id
                """).param("id", tournamentId).query(String.class).single();
    }

    String canonicalRegistrations(String snapshot) {
        return jdbc.sql("SELECT CAST(guest_claim_registration_snapshot(CAST(:snapshot AS jsonb)) AS text)")
                .param("snapshot", snapshot).query(String.class).single();
    }

    String canonicalSnapshot(String snapshot) {
        return jdbc.sql("SELECT CAST(guest_claim_conduct_snapshot(CAST(:snapshot AS jsonb)) AS text)")
                .param("snapshot", snapshot).query(String.class).single();
    }

    List<TournamentConductHistory.RegistrationDifference> registrationDifferences(String before, String current) {
        return jdbc.sql("""
                SELECT CAST(COALESCE(b.item, c.item)->>0 AS bigint) AS registration_id,
                       concat_ws(' ', p.nombre, p.apellido) AS player_name,
                       b.item->>2 AS old_status, c.item->>2 AS current_status
                FROM jsonb_array_elements(CAST(:before AS jsonb)) b(item)
                FULL JOIN jsonb_array_elements(CAST(:current AS jsonb)) c(item) ON b.item->>0 = c.item->>0
                LEFT JOIN player_padel_db p ON p.id = CAST(COALESCE(b.item, c.item)->>1 AS bigint)
                WHERE b.item IS DISTINCT FROM c.item ORDER BY registration_id
                """).param("before", before).param("current", current)
                .query((rs, n) -> new TournamentConductHistory.RegistrationDifference(rs.getLong("registration_id"),
                        rs.getString("player_name"), rs.getString("old_status"), rs.getString("current_status")))
                .list();
    }

    String activeRoster(String snapshot, TournamentType type) {
        String roster = switch (type) {
            case KING_OF_COURT -> "'king_of_court_player_stats_db'";
            case AMERICANO, AMERICANO_TEAMS -> "'americano_players_db'";
            default -> throw new IllegalArgumentException("Unsupported tournament format");
        };
        return jdbc.sql("""
                SELECT CAST(jsonb_build_array(
                    (SELECT COALESCE(jsonb_agg(jsonb_build_array(p->'player_id', p->'status') ORDER BY p->'player_id'),
                        '[]'::jsonb) FROM jsonb_array_elements(CAST(:snapshot AS jsonb)->%s) p),
                    (SELECT COALESCE(jsonb_agg(jsonb_build_array(t->'player1_id', t->'player2_id', t->'player2_name',
                        t->'status', t->'attended') ORDER BY t->'id'), '[]'::jsonb)
                        FROM jsonb_array_elements(CAST(:snapshot AS jsonb)->'americano_teams_db') t)
                ) AS text)
                """.formatted(roster)).param("snapshot", snapshot).query(String.class).single();
    }

    TournamentType type(long tournamentId) {
        return jdbc.sql("SELECT tipo FROM tournaments_db WHERE id = :id")
                .param("id", tournamentId).query(TournamentType.class).single();
    }

    boolean cancelled(long tournamentId) {
        return jdbc.sql("SELECT estado = 'CANCELADO' FROM tournaments_db WHERE id = :id")
                .param("id", tournamentId).query(Boolean.class).single();
    }

    void lockRegistrations(long tournamentId) {
        jdbc.sql("SELECT id FROM tournament_registrations_db WHERE tournament_id = :id ORDER BY id FOR SHARE")
                .param("id", tournamentId).query(Long.class).list();
    }

    void keepTeamDetails(long tournamentId) {
        jdbc.sql("""
                INSERT INTO tournament_conduct_team_details
                    (team_id, tournament_id, player1_id, has_paid, admin_comment, player2_phone)
                SELECT id, tournament_id, player1_id, has_paid, admin_comment, player2_phone
                FROM americano_teams_db WHERE tournament_id = :id FOR SHARE
                ON CONFLICT (team_id) DO UPDATE SET player1_id = EXCLUDED.player1_id, has_paid = EXCLUDED.has_paid,
                    admin_comment = EXCLUDED.admin_comment, player2_phone = EXCLUDED.player2_phone
                """).param("id", tournamentId).update();
    }

    void restore(long tournamentId, TournamentType type, String snapshot) {
        List<SnapshotTable> tables = tables(type);
        String youtube = null;
        if (type == TournamentType.KING_OF_COURT) {
            youtube = jdbc.sql("""
                    SELECT youtube_link FROM tournament_king_of_court_db WHERE tournament_id = :id
                    ORDER BY is_active DESC, id DESC LIMIT 1
                    """).param("id", tournamentId).query(String.class).optional().orElse(null);
        } else {
            keepTeamDetails(tournamentId);
        }
        for (int i = tables.size() - 1; i >= 0; i--) {
            SnapshotTable table = tables.get(i);
            jdbc.sql("DELETE FROM " + table.name() + " WHERE " + table.scope())
                    .param("id", tournamentId).update();
        }
        for (SnapshotTable table : tables) {
            jdbc.sql(table.restoreSql()).param("snapshot", snapshot).param("id", tournamentId).update();
        }
        if (youtube != null) {
            jdbc.sql("UPDATE " + KING + " SET youtube_link = :link WHERE tournament_id = :id")
                    .param("link", youtube).param("id", tournamentId).update();
        }
        jdbc.sql("""
                UPDATE tournaments_db
                SET estado = CAST(:snapshot AS jsonb)->>'status',
                    americano_final_order = CAST(:snapshot AS jsonb)->>'americanoFinalOrder'
                WHERE id = :id AND estado <> 'CANCELADO'
                  AND (estado = 'FINALIZADO' OR CAST(:snapshot AS jsonb)->>'status' = 'FINALIZADO')
                """).param("snapshot", snapshot).param("id", tournamentId).update();
    }

    private List<SnapshotTable> tables(TournamentType type) {
        return switch (type) {
            case KING_OF_COURT -> KING_TABLES;
            case AMERICANO -> java.util.stream.Stream.concat(AMERICANO_TABLES.stream(), java.util.stream.Stream.of(POINTS)).toList();
            case AMERICANO_TEAMS -> AMERICANO_TABLES;
            default -> throw new IllegalArgumentException("Unsupported tournament format");
        };
    }

    record Target(long tournamentId, Integer roundNumber, Integer courtNumber, String stage) {}

    private record SnapshotTable(String name, String scope, String order, String... excluded) {
        private SnapshotTable(String name, String scope) {
            this(name, scope, "id");
        }

        String snapshotExpression() {
            String row = "to_jsonb(r)";
            if (excluded.length > 0) {
                row += " - '{" + String.join(",", excluded) + "}'::text[]";
            }
            return "'%s', (SELECT COALESCE(jsonb_agg(%s ORDER BY %s), '[]'::jsonb) FROM %s r WHERE %s)"
                    .formatted(name, row, order, name, scope);
        }

        String restoreSql() {
            String rows = "jsonb_populate_recordset(NULL::" + name + ", CAST(:snapshot AS jsonb)->'" + name + "')";
            if (TEAM.equals(name)) {
                return """
                        INSERT INTO americano_teams_db
                        SELECT restored.* FROM jsonb_array_elements(CAST(:snapshot AS jsonb)->'americano_teams_db') item
                        LEFT JOIN tournament_conduct_team_details d ON d.team_id = (item->>'id')::bigint
                            AND d.tournament_id = :id AND d.player1_id = (item->>'player1_id')::bigint
                        CROSS JOIN LATERAL jsonb_populate_record(NULL::americano_teams_db,
                            item || jsonb_build_object('has_paid', COALESCE(d.has_paid, false),
                                'admin_comment', d.admin_comment, 'player2_phone', d.player2_phone)) restored
                        """;
            }
            return "INSERT INTO " + name + " SELECT restored.* FROM " + rows + " restored";
        }
    }
}
