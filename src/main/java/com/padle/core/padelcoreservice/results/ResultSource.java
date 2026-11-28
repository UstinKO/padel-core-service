package com.padle.core.padelcoreservice.results;

import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import org.springframework.jdbc.UncategorizedSQLException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
class ResultSource {
    private final JdbcTemplate jdbc;

    record Team(long id, Integer number, Long first, Long second, String guestName, String guestPhone, String status) {}
    record RawGame(String source, long id, Long resultId, Integer round, String stage, String court,
                   Long team1, List<Long> first, String firstFallback, Long team2, List<Long> second,
                   String secondFallback, Integer score1, Integer score2, String unit, String sets,
                   List<Long> winner, String status, String technical) {}

    ResultReport.Event event(long id) {
        return jdbc.query("""
                SELECT t.id,t.nombre,t.fecha_inicio,t.hora_inicio,t.time_zone,t.estado,t.is_active,
                       t.categoria_nivel,t.tipo,t.modalidad,t.americano_final_order,
                       o.organization_name,c.nombre AS club
                FROM tournaments_db t LEFT JOIN owners_db o ON o.id=t.owner_id
                LEFT JOIN club_db c ON c.id=t.club_id WHERE t.id=?
                """, (rs, row) -> new ResultReport.Event(rs.getLong("id"),rs.getString("nombre"),
                rs.getObject("fecha_inicio",LocalDate.class),rs.getObject("hora_inicio",LocalTime.class),
                rs.getString("time_zone"),rs.getString("organization_name"),rs.getString("club"),
                rs.getString("categoria_nivel"),rs.getString("tipo"),rs.getString("modalidad"),
                rs.getString("estado"),rs.getBoolean("is_active"),rs.getString("americano_final_order")), id)
                .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));
    }

    Optional<Long> king(long id) {
        return jdbc.query("SELECT id FROM tournament_king_of_court_db WHERE tournament_id=? ORDER BY is_active DESC,id DESC LIMIT 1",
                (rs, row) -> rs.getLong(1),id).stream().findFirst();
    }

    boolean kingFinished(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT is_finished FROM tournament_king_of_court_db WHERE id=?",Boolean.class,id));
    }

    List<Team> teams(long id, boolean contacts) {
        return jdbc.query("SELECT id,team_number,player1_id,player2_id,player2_name,CASE WHEN ? THEN player2_phone END,status FROM americano_teams_db WHERE tournament_id=? ORDER BY id",
                (rs, row) -> new Team(rs.getLong(1),rs.getObject(2,Integer.class),rs.getObject(3,Long.class),
                        rs.getObject(4,Long.class),rs.getString(5),rs.getString(6),rs.getString(7)),contacts,id);
    }

    List<ResultReport.Person> people(long id, boolean contacts, List<RawGame> games, List<Team> teams, Optional<Long> king) {
        var registered = jdbc.query("SELECT player_id FROM tournament_registrations_db WHERE tournament_id=? UNION SELECT player_id FROM americano_players_db WHERE tournament_id=?",(rs,row)->rs.getLong(1),id,id);
        var played = games.stream().flatMap(game -> Stream.concat(game.first().stream(),game.second().stream()));
        var paired = teams.stream().flatMap(team -> Stream.of(team.first(),team.second())).filter(Objects::nonNull);
        var ranked = king.map(run -> jdbc.query("SELECT player_id FROM king_of_court_player_stats_db WHERE tournament_king_id=?",
                (rs,row)->rs.getLong(1),run)).orElse(List.of());
        var ids = Stream.of(registered.stream(),played,paired,ranked.stream()).flatMap(stream->stream).distinct().sorted().toList();
        if (ids.isEmpty()) return List.of();
        var args = Stream.concat(Stream.of(contacts,id),ids.stream()).toArray();
        var placeholders = ids.stream().map(value -> "?").collect(Collectors.joining(","));
        return jdbc.query("""
                SELECT p.id,p.nombre,p.apellido,CASE WHEN ? THEN p.telefono END AS phone,
                       coalesce(r.status,'NO_REGISTRATION') AS status,r.is_active AS registration_active
                FROM player_padel_db p LEFT JOIN LATERAL
                (SELECT status,is_active FROM tournament_registrations_db WHERE player_id=p.id AND tournament_id=?
                 ORDER BY is_active DESC,id DESC LIMIT 1) r ON true WHERE p.id IN (
                """ + placeholders + ") ORDER BY p.id", (rs,row) -> person(rs,teams),args);
    }

    private ResultReport.Person person(ResultSet rs, List<Team> teams) throws SQLException {
        long id=rs.getLong("id");
        var linked=teams.stream().filter(team->Objects.equals(team.first(),id)||Objects.equals(team.second(),id)).toList();
        var partners=linked.stream().flatMap(team->Stream.of(team.first(),team.second())).filter(Objects::nonNull)
                .filter(player->player!=id).distinct().toList();
        return new ResultReport.Person(id,rs.getString("nombre"),rs.getString("apellido"),rs.getString("phone"),rs.getString("status"),
                joined(linked.stream().map(Team::id)),joined(linked.stream().map(Team::number)),joined(partners.stream()),rs.getObject("registration_active",Boolean.class));
    }

    private String joined(Stream<?> values) { return values.map(String::valueOf).collect(Collectors.joining(";")); }

    List<RawGame> games(ResultReport.Event event, Optional<Long> king) {
        if ("KING_OF_COURT".equals(event.format())) return king.map(run->jdbc.query(KING,GAME,run)).orElse(List.of());
        var games=jdbc.query(AMERICANO,GAME,event.id());
        return games.isEmpty() ? jdbc.query(LEGACY,GAME,event.id()) : games;
    }

    private static List<Long> ids(Array value) throws SQLException {
        return Optional.ofNullable(value).map(array -> {
            try { return Arrays.stream((Object[])array.getArray()).filter(Objects::nonNull)
                    .map(number->((Number)number).longValue()).toList(); }
            catch (SQLException error) { throw new UncategorizedSQLException("Read result IDs",null,error); }
        }).orElse(List.of());
    }

    private static final RowMapper<RawGame> GAME=(rs,row)->new RawGame(rs.getString("source"),rs.getLong("id"),
            rs.getObject("result_id",Long.class),rs.getObject("round_number",Integer.class),rs.getString("stage"),rs.getString("court"),
            rs.getObject("team1",Long.class),ids(rs.getArray("first_players")),rs.getString("first_fallback"),
            rs.getObject("team2",Long.class),ids(rs.getArray("second_players")),rs.getString("second_fallback"),
            rs.getObject("score1",Integer.class),rs.getObject("score2",Integer.class),rs.getString("unit"),rs.getString("sets"),
            ids(rs.getArray("winner")),rs.getString("status"),rs.getString("technical"));

    private static final String AMERICANO="""
            SELECT 'AMERICANO' AS source,m.id,NULL::bigint AS result_id,r.round_number,
              coalesce(m.playoff_stage,CASE WHEN m.placement_start IS NOT NULL THEN 'PLACEMENT_'||m.placement_start END,r.phase,'ROUND_ROBIN') AS stage,
              m.court_number::text AS court,m.team1_id AS team1,m.team2_id AS team2,
              ARRAY[coalesce(m.team1_player1_id,t1.player1_id),coalesce(m.team1_player2_id,t1.player2_id)] AS first_players,
              ARRAY[coalesce(m.team2_player1_id,t2.player1_id),coalesce(m.team2_player2_id,t2.player2_id)] AS second_players,
              CASE WHEN t1.player2_id IS NULL THEN t1.player2_name END AS first_fallback,
              CASE WHEN t2.player2_id IS NULL THEN t2.player2_name END AS second_fallback,
              coalesce(m.team1_games,m.team1_score) AS score1,coalesce(m.team2_games,m.team2_score) AS score2,
              CASE WHEN m.team1_games IS NOT NULL OR r.scoring_mode='SETS' THEN 'GAMES' ELSE 'POINTS' END AS unit,
              NULL::text AS sets,m.status,
              CASE WHEN m.status='COMPLETED' AND coalesce(m.team1_games,m.team1_score)>coalesce(m.team2_games,m.team2_score)
                THEN ARRAY[coalesce(m.team1_player1_id,t1.player1_id),coalesce(m.team1_player2_id,t1.player2_id)]
                WHEN m.status='COMPLETED' AND coalesce(m.team2_games,m.team2_score)>coalesce(m.team1_games,m.team1_score)
                THEN ARRAY[coalesce(m.team2_player1_id,t2.player1_id),coalesce(m.team2_player2_id,t2.player2_id)] END AS winner,
              CASE WHEN upper(btrim(m.note)) IN ('BYE','WALKOVER','NO_SHOW','NO SHOW','FORFEIT','TECHNICAL') THEN upper(btrim(m.note)) END AS technical
            FROM americano_matches_db m JOIN americano_rounds_db r ON r.id=m.round_id
            LEFT JOIN americano_teams_db t1 ON t1.id=m.team1_id LEFT JOIN americano_teams_db t2 ON t2.id=m.team2_id
            WHERE m.tournament_id=? ORDER BY r.round_number,m.match_number,m.id
            """;

    private static final String KING="""
            SELECT 'KING_OF_COURT' AS source,c.id,m.id AS result_id,r.round_number,'ROUND' AS stage,c.court_number::text AS court,
              NULL::bigint AS team1,NULL::bigint AS team2,
              CASE WHEN m.id IS NOT NULL THEN ARRAY[m.winner1_id,m.winner2_id] ELSE ARRAY[t1.player1_id,t1.player2_id] END AS first_players,
              CASE WHEN m.id IS NOT NULL THEN ARRAY[m.loser1_id,m.loser2_id] ELSE ARRAY[t2.player1_id,t2.player2_id] END AS second_players,
              NULL::text AS first_fallback,NULL::text AS second_fallback,m.winners_score AS score1,m.losers_score AS score2,
              'POINTS' AS unit,NULL::text AS sets,ARRAY[m.winner1_id,m.winner2_id] AS winner,
              CASE WHEN m.id IS NOT NULL THEN 'COMPLETED' ELSE 'IN_PROGRESS' END AS status,NULL::text AS technical
            FROM king_of_court_court_db c JOIN king_of_court_round_db r ON r.id=c.round_id
            LEFT JOIN king_of_court_match_result_db m ON m.court_id=c.id
            LEFT JOIN court_teams_db t1 ON t1.court_id=c.id AND t1.team_number=1
            LEFT JOIN court_teams_db t2 ON t2.court_id=c.id AND t2.team_number=2
            WHERE r.tournament_king_id=? ORDER BY r.round_number,c.court_number,c.id
            """;

    private static final String LEGACY="""
            SELECT 'BRACKET' AS source,id,NULL::bigint AS result_id,ronda AS round_number,'ROUND' AS stage,cancha AS court,
              NULL::bigint AS team1,NULL::bigint AS team2,ARRAY[player1_id,player3_id] AS first_players,
              ARRAY[player2_id,player4_id] AS second_players,equipo1_nombre AS first_fallback,equipo2_nombre AS second_fallback,
              puntos_equipo1 AS score1,puntos_equipo2 AS score2,
              CASE WHEN puntos_equipo1 IS NOT NULL OR puntos_equipo2 IS NOT NULL THEN 'POINTS' ELSE 'SETS' END AS unit,
              concat_ws(', ',CASE WHEN set1_equipo1 IS NOT NULL AND set1_equipo2 IS NOT NULL THEN set1_equipo1||':'||set1_equipo2 END,
                CASE WHEN set2_equipo1 IS NOT NULL AND set2_equipo2 IS NOT NULL THEN set2_equipo1||':'||set2_equipo2 END,
                CASE WHEN set3_equipo1 IS NOT NULL AND set3_equipo2 IS NOT NULL THEN set3_equipo1||':'||set3_equipo2 END,
                CASE WHEN super_set_equipo1 IS NOT NULL AND super_set_equipo2 IS NOT NULL THEN 'super '||super_set_equipo1||':'||super_set_equipo2 END) AS sets,
              CASE WHEN estado IN ('FINALIZADO','WALKOVER') THEN ARRAY[ganador_id] END AS winner,estado AS status,
              CASE WHEN estado='WALKOVER' THEN 'WALKOVER' END AS technical
            FROM matches_db WHERE tournament_id=? AND is_active=true ORDER BY ronda,partido_numero,id
            """;
}
