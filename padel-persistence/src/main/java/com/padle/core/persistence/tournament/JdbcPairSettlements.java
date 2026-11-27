package com.padle.core.persistence.tournament;

import com.padle.core.application.registration.PairSettlements;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcPairSettlements implements PairSettlements {
    private final JdbcTemplate jdbc;

    @Override
    public List<Settlement> forTournament(long tournamentId) {
        return jdbc.query("""
                SELECT booking_id, participants, no_show, currency, obligation, already_paid, remaining_due,
                       refundable, replacement_booking_id
                FROM pair_cancellations_db WHERE tournament_id = ? ORDER BY cancelled_at DESC
                """, (row, i) -> new Settlement(row.getObject("booking_id", UUID.class), row.getString("participants"),
                row.getBoolean("no_show"), row.getString("currency"), row.getBigDecimal("obligation"),
                row.getBigDecimal("already_paid"), row.getBigDecimal("remaining_due"), row.getBigDecimal("refundable"),
                row.getObject("replacement_booking_id", UUID.class)), tournamentId);
    }

    @Override
    public void recordBookerReview(UUID bookingId, long tournamentId, long playerId, long organizerId,
                                   LocalDateTime at) {
        jdbc.update("""
                INSERT INTO pair_booker_reviews_db (booking_id, tournament_id, player_id, reviewed_by, reviewed_at)
                VALUES (?, ?, ?, ?, ?)
                """, bookingId, tournamentId, playerId, organizerId, at);
    }

    @Override
    public void recordCancellation(Cancellation cancellation) {
        jdbc.update("""
                INSERT INTO pair_cancellations_db
                    (booking_id, tournament_id, cancelled_by, cancelled_at, terms_version, currency,
                     obligation, already_paid, remaining_due, refundable, participants, no_show)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, cancellation.bookingId(), cancellation.tournamentId(), cancellation.actorId(), cancellation.at(),
                cancellation.termsVersion(), cancellation.currency(), cancellation.obligation(), cancellation.paid(),
                cancellation.due(), cancellation.refund(), cancellation.participants(), cancellation.noShow());
    }

    @Override
    public void recordReplacement(UUID bookingId, long tournamentId, UUID replacementId, long organizerId,
                                  LocalDateTime at, BigDecimal paid) {
        jdbc.update("""
                UPDATE pair_cancellations_db SET replacement_booking_id = ?, replacement_confirmed_by = ?,
                    replacement_confirmed_at = ?, obligation = 0, remaining_due = 0, refundable = ?, already_paid = ?
                WHERE booking_id = ? AND tournament_id = ?
                """, replacementId, organizerId, at, paid, paid, bookingId, tournamentId);
    }
}
