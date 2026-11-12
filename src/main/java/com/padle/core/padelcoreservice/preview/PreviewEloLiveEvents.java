package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.model.enums.AmericanoFinalOrder;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Profile("preview")
@ConditionalOnProperty("app.preview.elo-history")
@RequiredArgsConstructor
class PreviewEloLiveEvents {
    private static final String SCORED_MATCHES = """
            SELECT t.id AS event_id, t.fecha_inicio, t.categoria_nivel, 'king' AS format,
                   k.id AS game_tournament, k.is_finished AS finished, NULL AS final_order,
                   m.id AS match_id, r.round_number AS batch, false AS final_match,
                   m.winners_score AS left_score, m.losers_score AS right_score,
                   m.winner1_id AS a, m.winner2_id AS b, m.loser1_id AS c, m.loser2_id AS d
              FROM king_of_court_match_result_db m
              JOIN king_of_court_court_db c ON c.id=m.court_id
              JOIN king_of_court_round_db r ON r.id=c.round_id
              JOIN tournament_king_of_court_db k ON k.id=r.tournament_king_id
              JOIN tournaments_db t ON t.id=k.tournament_id
             WHERE t.fecha_inicio > :cutoff AND t.is_active AND t.estado <> 'CANCELADO' AND r.is_completed
             UNION ALL
            SELECT t.id, t.fecha_inicio, t.categoria_nivel,
                   CASE WHEN t.tipo='AMERICANO_TEAMS' THEN 'pair' ELSE 'americano' END,
                   t.id, t.estado='FINALIZADO', t.americano_final_order, m.id,
                   CASE WHEN t.tipo='AMERICANO_TEAMS' THEN
                       CASE m.playoff_stage WHEN 'ROUND_OF_16' THEN 3 WHEN 'QUARTER_FINAL' THEN 4
                           WHEN 'SEMI_FINAL' THEN 5 WHEN 'FINAL' THEN 6
                           ELSE LEAST(r.round_number, 2) END
                       ELSE r.round_number END,
                   COALESCE(t.tipo='AMERICANO_TEAMS' AND m.playoff_stage='FINAL',false),
                   CASE WHEN t.tipo='AMERICANO_TEAMS' THEN m.team1_games ELSE m.team1_score END,
                   CASE WHEN t.tipo='AMERICANO_TEAMS' THEN m.team2_games ELSE m.team2_score END,
                   m.team1_player1_id,m.team1_player2_id,m.team2_player1_id,m.team2_player2_id
              FROM americano_matches_db m
              JOIN americano_rounds_db r ON r.id=m.round_id
              JOIN tournaments_db t ON t.id=m.tournament_id
             WHERE t.fecha_inicio > :cutoff AND t.is_active AND t.estado <> 'CANCELADO' AND m.status='COMPLETED'
               AND ((t.tipo='AMERICANO' AND t.modalidad='INDIVIDUAL' AND r.status='COMPLETED')
                    OR (t.tipo='AMERICANO_TEAMS' AND t.modalidad='DOBLES' AND m.team1_games IS NOT NULL
                        AND m.team2_games IS NOT NULL))
             ORDER BY fecha_inicio,event_id,batch,match_id
            """;

    private final JdbcClient jdbc;
    private final KingOfCourtService king;
    private final AmericanoService americano;

    private record NativeGame(long eventId, LocalDate day, String category, String format,
                              long formatId, boolean finished, String finalOrder, PreviewEloReplay.Game game) { }

    record LiveHistory(List<PreviewEloReplay.Event> events, Map<Long, String> names) { }

    LiveHistory load(LocalDate cutoff) {
        var nativeGames = jdbc.sql(SCORED_MATCHES).param("cutoff", cutoff)
                .query((row, index) -> map(row)).list().stream().filter(Objects::nonNull).toList();
        var groups = nativeGames.stream().collect(Collectors.groupingBy(NativeGame::eventId));
        var events = new ArrayList<PreviewEloReplay.Event>();
        for (var tournament : groups.values()) {
            var first = tournament.get(0);
            events.add(new PreviewEloReplay.Event("S-" + first.eventId(), first.day(), first.category(),
                    tournament.stream().map(NativeGame::game).toList(), champions(first)));
        }
        var ids = nativeGames.stream().flatMap(item -> item.game().ids().stream()).collect(Collectors.toSet());
        var names = ids.isEmpty() ? Map.<Long, String>of() : jdbc.sql("""
                SELECT id,concat_ws(' ',nombre,apellido) AS display_name FROM player_padel_db WHERE id IN (:ids)
                """).param("ids", ids).query((row, index) -> Map.entry(row.getLong(1), row.getString(2)))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new LiveHistory(List.copyOf(events), names);
    }

    private Set<Long> champions(NativeGame first) {
        if (!first.finished()) {
            return Set.of();
        }
        return switch (first.format()) {
            case "king" -> king.getRanking(first.formatId()).stream().limit(1)
                    .map(player -> player.getPlayerId()).collect(Collectors.toSet());
            case "americano" -> {
                var order = first.finalOrder() == null ? AmericanoFinalOrder.SCORE_DESC
                        : AmericanoFinalOrder.valueOf(first.finalOrder());
                yield americano.getRanking(first.eventId(), order.sortBy(), order.ascending() ? "ASC" : "DESC")
                        .stream().limit(1).map(player -> player.getPlayerId()).collect(Collectors.toSet());
            }
            default -> Set.of();
        };
    }

    private NativeGame map(ResultSet row) throws SQLException {
        var ids = new ArrayList<Long>();
        for (var column : List.of("a", "b", "c", "d")) {
            var id = row.getObject(column, Long.class);
            if (id == null || ids.contains(id)) {
                return null;
            }
            ids.add(id);
        }
        var left = row.getObject("left_score", Integer.class);
        var right = row.getObject("right_score", Integer.class);
        if (left == null || right == null) {
            return null;
        }
        var format = row.getString("format");
        var game = new PreviewEloReplay.Game(format + ":" + row.getLong("match_id"), row.getInt("batch"), ids,
                left, right, row.getBoolean("final_match"));
        return new NativeGame(row.getLong("event_id"), row.getObject("fecha_inicio", LocalDate.class),
                row.getString("categoria_nivel"), format, row.getLong("game_tournament"),
                row.getBoolean("finished"), row.getString("final_order"), game);
    }
}
