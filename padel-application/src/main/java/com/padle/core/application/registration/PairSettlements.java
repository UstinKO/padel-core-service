package com.padle.core.application.registration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PairSettlements {
    List<Settlement> forTournament(long tournamentId);

    void recordCancellation(Cancellation cancellation);

    void recordBookerReview(UUID bookingId, long tournamentId, long playerId, long organizerId, LocalDateTime at);

    void recordReplacement(UUID bookingId, long tournamentId, UUID replacementId, long organizerId,
                           LocalDateTime at, BigDecimal paid);

    record Settlement(UUID bookingId, String participants, boolean noShow, String currency,
                      BigDecimal obligation, BigDecimal paid, BigDecimal due, BigDecimal refund, UUID replacementBookingId) {
    }

    record Cancellation(UUID bookingId, long tournamentId, long actorId, LocalDateTime at, int termsVersion,
                        String currency, BigDecimal obligation, BigDecimal paid, BigDecimal due, BigDecimal refund,
                        String participants, boolean noShow) {
    }
}
