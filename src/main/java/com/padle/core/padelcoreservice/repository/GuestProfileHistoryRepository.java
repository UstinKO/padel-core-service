package com.padle.core.padelcoreservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class GuestProfileHistoryRepository {
    private final JdbcClient jdbc;

    private static final String PARTICIPATION = """
            SELECT tournament_id, player_id FROM tournament_registrations_db
            UNION ALL SELECT tournament_id, player_id FROM registration_cancellation_members
                JOIN registration_cancellations ON registration_cancellations.id = event_id
            UNION ALL SELECT tournament_id, player_id FROM americano_players_db
            UNION ALL SELECT t.tournament_id, p.player_id FROM americano_teams_db t
                CROSS JOIN LATERAL (VALUES (t.player1_id), (t.player2_id)) p(player_id)
            UNION ALL SELECT t.tournament_id, s.player_id FROM king_of_court_player_stats_db s
                JOIN tournament_king_of_court_db t ON t.id = s.tournament_king_id
            UNION ALL SELECT t.tournament_id, p.player_id FROM court_players_db p
                JOIN king_of_court_court_db c ON c.id = p.court_id
                JOIN king_of_court_round_db r ON r.id = c.round_id
                JOIN tournament_king_of_court_db t ON t.id = r.tournament_king_id
            UNION ALL SELECT t.tournament_id, p.player_id FROM court_teams_db ct
                JOIN king_of_court_court_db c ON c.id = ct.court_id
                JOIN king_of_court_round_db r ON r.id = c.round_id
                JOIN tournament_king_of_court_db t ON t.id = r.tournament_king_id
                CROSS JOIN LATERAL (VALUES (ct.player1_id), (ct.player2_id)) p(player_id)
            UNION ALL SELECT tournament_id, player_id FROM tournament_additional_points
            UNION ALL SELECT tournament_id, player1_id FROM tournament_conduct_team_details
            UNION ALL SELECT tournament_id, player_id FROM match_players
            """;

    private static final String MATCH_PLAYERS = """
            SELECT 'match' AS format, m.id, m.tournament_id, p.player_id, m.ganador_id IS NOT NULL AS played
              FROM matches_db m
              CROSS JOIN LATERAL (VALUES (m.player1_id), (m.player2_id), (m.player3_id), (m.player4_id)) p(player_id)
            UNION ALL
            SELECT 'americano', m.id, m.tournament_id, p.player_id,
                   (m.team1_score IS NOT NULL AND m.team2_score IS NOT NULL) OR
                   (m.team1_games IS NOT NULL AND m.team2_games IS NOT NULL)
              FROM americano_matches_db m
              CROSS JOIN LATERAL (VALUES (m.team1_player1_id), (m.team1_player2_id),
                  (m.team2_player1_id), (m.team2_player2_id)) p(player_id)
            UNION ALL
            SELECT 'king', m.id, t.tournament_id, p.player_id, true
              FROM king_of_court_match_result_db m
              JOIN king_of_court_court_db c ON c.id = m.court_id
              JOIN king_of_court_round_db r ON r.id = c.round_id
              JOIN tournament_king_of_court_db t ON t.id = r.tournament_king_id
              CROSS JOIN LATERAL (VALUES (m.winner1_id), (m.winner2_id), (m.loser1_id), (m.loser2_id)) p(player_id)
            """;

    public List<String> conflicts(long guestId, long accountId) {
        var tournaments = jdbc.sql("WITH match_players AS (" + MATCH_PLAYERS + "), participation AS ("
                + PARTICIPATION + """
                )
                SELECT tournament_id FROM participation
                WHERE player_id IN (:guest, :account)
                GROUP BY tournament_id HAVING count(DISTINCT player_id) = 2
                ORDER BY tournament_id LIMIT 20
                """).param("guest", guestId).param("account", accountId).query(Long.class).list();
        List<String> conflicts = new ArrayList<>();
        if (!tournaments.isEmpty()) {
            conflicts.add("GUEST_CLAIM_HISTORY_CONFLICT");
            tournaments.forEach(id -> conflicts.add("TOURNAMENT:" + id));
        }
        if (jdbc.sql("""
                SELECT count(DISTINCT player_id) = 2 FROM ranking_db WHERE player_id IN (:guest, :account)
                """).param("guest", guestId).param("account", accountId).query(Boolean.class).single()) {
            conflicts.add("GUEST_CLAIM_RANKING_CONFLICT");
        }
        return List.copyOf(conflicts);
    }

    public Counts counts(long playerId) {
        return jdbc.sql("WITH match_players AS (" + MATCH_PLAYERS + """
                )
                SELECT (SELECT count(*) FROM tournament_registrations_db WHERE player_id = :player) AS registrations,
                       (SELECT count(*) FROM (SELECT DISTINCT format, id FROM match_players
                         WHERE player_id = :player AND played) results) AS results
                """).param("player", playerId).query(Counts.class).single();
    }

    public record Counts(long registrations, long results) {}

    public void transfer(long guestId, long accountId, String accountEmail) {
        // Сохраняем первичные ключи строк, включая регистрации и связанные с ними оплаты.
        jdbc.sql("""
                UPDATE tournament_registrations_db
                SET partner_registration_token = NULL, partner_token_expiry = NULL, partner_action_token = NULL
                WHERE partner_id = :guest
                """).param("guest", guestId).update();
        jdbc.sql("""
                UPDATE tournament_registrations_db SET waitlist_confirmation_token = NULL WHERE player_id = :guest
                """).param("guest", guestId).update();
        transferReferences(guestId, accountId);
        updatePartnerEmail(accountId, accountEmail);
    }

    public void updatePartnerEmail(long accountId, String accountEmail) {
        jdbc.sql("""
                UPDATE tournament_registrations_db SET partner_email = :email
                WHERE partner_id = :account AND (partner_email IS NULL OR lower(partner_email) LIKE '%@1padel.guest')
                """).param("email", accountEmail).param("account", accountId).update();
    }

    private void transferReferences(long guestId, long accountId) {
        for (var reference : REFERENCES) {
            jdbc.sql("UPDATE %s SET %s = :account WHERE %s = :guest"
                            .formatted(reference.table(), reference.column(), reference.column()))
                    .param("guest", guestId).param("account", accountId).update();
        }
    }

    private record Reference(String table, String column) {}

    private static final List<Reference> REFERENCES = List.of(
            new Reference("tournament_registrations_db", "player_id"),
            new Reference("tournament_registrations_db", "partner_id"),
            new Reference("tournament_registrations_db", "main_player_id"),
            new Reference("tournament_registrations_db", "pair_booked_by"),
            new Reference("tournament_registrations_db", "pair_terms_accepted_by"),
            new Reference("participation_requests_db", "delegate_player_id"),
            new Reference("americano_players_db", "player_id"),
            new Reference("americano_teams_db", "player1_id"),
            new Reference("americano_teams_db", "player2_id"),
            new Reference("americano_matches_db", "team1_player1_id"),
            new Reference("americano_matches_db", "team1_player2_id"),
            new Reference("americano_matches_db", "team2_player1_id"),
            new Reference("americano_matches_db", "team2_player2_id"),
            new Reference("court_players_db", "player_id"),
            new Reference("court_teams_db", "player1_id"),
            new Reference("court_teams_db", "player2_id"),
            new Reference("king_of_court_match_result_db", "winner1_id"),
            new Reference("king_of_court_match_result_db", "winner2_id"),
            new Reference("king_of_court_match_result_db", "loser1_id"),
            new Reference("king_of_court_match_result_db", "loser2_id"),
            new Reference("king_of_court_player_stats_db", "player_id"),
            new Reference("matches_db", "player1_id"),
            new Reference("matches_db", "player2_id"),
            new Reference("matches_db", "player3_id"),
            new Reference("matches_db", "player4_id"),
            new Reference("matches_db", "ganador_id"),
            new Reference("matches_db", "perdedor_id"),
            new Reference("ranking_db", "player_id"),
            new Reference("pair_booker_reviews_db", "player_id"),
            new Reference("pair_replacement_notices_db", "player_id"),
            new Reference("tournament_additional_points", "player_id"),
            new Reference("tournament_conduct_team_details", "player1_id"));
}
