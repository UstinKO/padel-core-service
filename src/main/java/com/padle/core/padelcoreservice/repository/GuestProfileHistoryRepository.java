package com.padle.core.padelcoreservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class GuestProfileHistoryRepository {
    private final JdbcClient jdbc;

    public boolean hasConflict(long guestId, long accountId) {
        return jdbc.sql("""
                WITH participation AS (
                    SELECT tournament_id, player_id FROM tournament_registrations_db
                    UNION ALL SELECT tournament_id, player_id FROM registration_cancellation_members
                        JOIN registration_cancellations ON registration_cancellations.id = event_id
                    UNION ALL SELECT tournament_id, player_id FROM americano_players_db
                    UNION ALL SELECT tournament_id, player1_id FROM americano_teams_db
                    UNION ALL SELECT tournament_id, player2_id FROM americano_teams_db
                    UNION ALL SELECT t.tournament_id, s.player_id FROM king_of_court_player_stats_db s
                        JOIN tournament_king_of_court_db t ON t.id = s.tournament_king_id
                    UNION ALL SELECT tournament_id, player1_id FROM matches_db
                    UNION ALL SELECT tournament_id, player2_id FROM matches_db
                    UNION ALL SELECT tournament_id, player3_id FROM matches_db
                    UNION ALL SELECT tournament_id, player4_id FROM matches_db
                )
                SELECT EXISTS (
                    SELECT 1 FROM participation
                    WHERE player_id IN (:guest, :account)
                    GROUP BY tournament_id HAVING count(DISTINCT player_id) = 2
                ) OR (
                    EXISTS (SELECT 1 FROM ranking_db WHERE player_id = :guest)
                    AND EXISTS (SELECT 1 FROM ranking_db WHERE player_id = :account)
                )
                """).param("guest", guestId).param("account", accountId).query(Boolean.class).single();
    }

    public void transfer(long guestId, long accountId, String accountEmail) {
        // Сохраняем первичные ключи строк, включая регистрации и связанные с ними оплаты.
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
