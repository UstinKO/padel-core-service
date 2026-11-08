package com.padle.core.padelcoreservice.service.conduct;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.conduct.AdditionalPointsStore.Entry;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdditionalPointsService {

    private final JdbcClient jdbc;
    private final AdditionalPointsStore store;
    private final TournamentAccessService access;
    private final TournamentConductHistory history;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Editor editor(Long tournamentId, boolean finishedResults, Owner owner) {
        access.assertCanManageTournament(owner, tournamentId);
        long version = history.revision(tournamentId);
        Context context = context(tournamentId, finishedResults);
        return new Editor(version, players(context), rounds(context), sheet(context).entries(), audit(tournamentId));
    }

    @ConductChange(ConductChange.Action.ADD_POINTS)
    public Entry add(Long tournamentId, boolean finishedResults, Change change, Owner owner) {
        Context context = authorizedContext(tournamentId, finishedResults, owner);
        return save(context, validated(context, UUID.randomUUID(), change));
    }

    @ConductChange(ConductChange.Action.EDIT_POINTS)
    public Entry edit(Long tournamentId, boolean finishedResults, UUID id, Change change, Owner owner) {
        Context context = authorizedContext(tournamentId, finishedResults, owner);
        activeEntry(context, id);
        return save(context, validated(context, id, change));
    }

    @ConductChange(ConductChange.Action.CANCEL_POINTS)
    public Entry cancel(Long tournamentId, boolean finishedResults, UUID id, Owner owner) {
        Context context = authorizedContext(tournamentId, finishedResults, owner);
        Entry old = activeEntry(context, id);
        return save(context, new Entry(old.id(), old.playerId(), old.roundNumber(), old.delta(), old.note(), true));
    }

    private Context authorizedContext(long tournamentId, boolean finishedResults, Owner owner) {
        access.assertCanManageTournament(owner, tournamentId);
        return context(tournamentId, finishedResults);
    }

    private Context context(long tournamentId, boolean finishedResults) {
        var state = jdbc.sql("""
                SELECT t.tipo, t.modalidad, t.estado, k.id AS king_id, k.is_finished
                FROM tournaments_db t
                LEFT JOIN LATERAL (
                    SELECT id, is_finished FROM tournament_king_of_court_db
                    WHERE tournament_id = t.id ORDER BY is_active DESC, id DESC LIMIT 1
                ) k ON t.tipo = 'KING_OF_COURT'
                WHERE t.id = :id
                """).param("id", tournamentId).query((rs, row) -> new TournamentState(
                TournamentType.valueOf(rs.getString("tipo")), rs.getString("modalidad"), rs.getString("estado"),
                rs.getObject("king_id", Long.class), rs.getBoolean("is_finished"))).single();

        if ("CANCELADO".equals(state.status()) || !state.supported()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "additional.points.unavailable");
        }
        boolean finished = state.type() == TournamentType.KING_OF_COURT
                ? state.finishedKing() : "FINALIZADO".equals(state.status());
        Context context = new Context(tournamentId, state.kingId());
        if (finished != finishedResults || rounds(context).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "additional.points.unavailable");
        }
        return context;
    }

    private List<Player> players(Context context) {
        String roster = context.kingId() != null
                ? "king_of_court_player_stats_db WHERE tournament_king_id = :id"
                : "americano_players_db WHERE tournament_id = :id";
        return jdbc.sql("""
                SELECT p.id, concat_ws(' ', p.nombre, p.apellido) AS name
                FROM player_padel_db p JOIN (SELECT player_id FROM %s) roster ON roster.player_id = p.id
                ORDER BY name, p.id
                """.formatted(roster)).param("id", context.scopeId())
                .query((rs, row) -> new Player(rs.getLong("id"), rs.getString("name"))).list();
    }

    private List<Integer> rounds(Context context) {
        if (context.kingId() != null) {
            return jdbc.sql("""
                    SELECT round_number FROM king_of_court_round_db
                    WHERE tournament_king_id = :id ORDER BY round_number
                    """).param("id", context.kingId()).query(Integer.class).list();
        }
        return jdbc.sql("""
                WITH rounds AS (
                    SELECT round_number, status FROM americano_rounds_db WHERE tournament_id = :id
                ), latest AS (
                    SELECT round_number, status FROM rounds
                    WHERE status IN ('IN_PROGRESS', 'COMPLETED') ORDER BY round_number DESC LIMIT 1
                )
                SELECT round_number FROM rounds
                WHERE round_number <= COALESCE(
                    (SELECT round_number + CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END FROM latest), 0)
                   OR (round_number = 1 AND NOT EXISTS (SELECT 1 FROM latest))
                ORDER BY round_number
                """).param("id", context.tournamentId()).query(Integer.class).list();
    }

    private Entry validated(Context context, UUID id, Change change) {
        if (change.delta() == 0 || change.delta() == Integer.MIN_VALUE || change.note().length() > 500 || change.roundNumber() < 0
                || change.roundNumber() > 0 && !rounds(context).contains(change.roundNumber())
                || players(context).stream().noneMatch(player -> player.id() == change.playerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "additional.points.invalid");
        }
        return new Entry(id, change.playerId(), change.roundNumber(), change.delta(), change.note().strip(), false);
    }

    private Entry activeEntry(Context context, UUID id) {
        return sheet(context).entries().stream().filter(entry -> entry.id().equals(id) && !entry.cancelled())
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private AdditionalPointsStore.ScoreSheet sheet(Context context) {
        return context.kingId() != null ? store.forKing(context.kingId()) : store.forAmericano(context.tournamentId());
    }

    private Entry save(Context context, Entry entry) {
        store.save(context.tournamentId(), context.kingId(), entry);
        if (context.kingId() != null) {
            events.publishEvent(new KingPointsChanged(context.kingId()));
        }
        return entry;
    }

    private List<AuditEntry> audit(long tournamentId) {
        return jdbc.sql("""
                WITH versions AS (
                    SELECT id, recorded_at, actor_name, action,
                           snapshot->'tournament_additional_points' AS entries,
                           LAG(snapshot->'tournament_additional_points') OVER (ORDER BY id) AS previous
                    FROM tournament_conduct_history WHERE tournament_id = :id
                )
                SELECT v.recorded_at, v.actor_name, v.action, entry.*,
                       concat_ws(' ', p.nombre, p.apellido) AS player_name
                FROM versions v
                CROSS JOIN LATERAL jsonb_populate_recordset(NULL::tournament_additional_points, v.entries) entry
                JOIN player_padel_db p ON p.id = entry.player_id
                WHERE v.action IN ('ADD_POINTS', 'EDIT_POINTS', 'CANCEL_POINTS')
                  AND NOT EXISTS (
                      SELECT FROM jsonb_array_elements(v.previous) previous(item)
                      WHERE previous.item = to_jsonb(entry)
                  )
                ORDER BY v.id DESC
                """).param("id", tournamentId).query((rs, row) -> new AuditEntry(
                rs.getObject("recorded_at", OffsetDateTime.class), rs.getString("actor_name"),
                rs.getString("action"), rs.getString("player_name"), rs.getInt("round_number"),
                rs.getInt("delta"), rs.getString("note"))).list();
    }

    public record Editor(long conductVersion, List<Player> players, List<Integer> rounds,
                         List<Entry> entries, List<AuditEntry> history) {}
    public record Player(long id, String name) {}
    public record Change(long playerId, int roundNumber, int delta, String note) {}
    public record AuditEntry(OffsetDateTime time, String actor, String action, String playerName,
                             int roundNumber, int delta, String note) {}
    public record KingPointsChanged(long kingId) {}

    private record Context(long tournamentId, Long kingId) {
        long scopeId() {
            return kingId != null ? kingId : tournamentId;
        }
    }

    private record TournamentState(TournamentType type, String modalidad, String status, Long kingId, boolean finishedKing) {
        boolean supported() {
            return type == TournamentType.KING_OF_COURT && kingId != null
                    || type == TournamentType.AMERICANO && "INDIVIDUAL".equals(modalidad);
        }
    }
}
