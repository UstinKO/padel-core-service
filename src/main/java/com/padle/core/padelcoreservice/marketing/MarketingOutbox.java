package com.padle.core.padelcoreservice.marketing;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketingOutbox {
    private final JdbcTemplate jdbc;
    private final MetaProperties properties;
    private final MarketingContextService context;

    public record BrowserEvent(UUID eventId, String eventName, long tournamentId, String registrationStatus) { }
    public record Delivery(UUID eventId, long tournamentId, UUID visitorId, Instant occurredAt,
                           BigDecimal amount, String currency, int paidParticipants, int attempt,
                           String fbc, String fbp, String actionSource) { }

    public List<BrowserEvent> browserEvents(HttpServletRequest request) {
        if (!properties.browserEnabled() || !MarketingPolicy.hasConsent(request)) {
            return List.of();
        }
        return context.visitorId(request).map(id -> jdbc.query("""
                SELECT event_id, event_name, tournament_id, registration_status FROM marketing_events_db
                WHERE visitor_id = ? AND channel = 'BROWSER' AND state = 'PENDING'
                    AND occurred_at > CURRENT_TIMESTAMP - INTERVAL '47 hours' AND test_mode = ?
                ORDER BY occurred_at LIMIT 30
                """, (rs, row) -> new BrowserEvent(rs.getObject("event_id", UUID.class), rs.getString("event_name"),
                rs.getLong("tournament_id"), rs.getString("registration_status")), id, properties.testMode())).orElseGet(List::of);
    }

    @Transactional
    public void acknowledge(UUID eventId, HttpServletRequest request) {
        if (properties.browserEnabled() && MarketingPolicy.hasConsent(request)) {
            context.visitorId(request).ifPresent(id -> jdbc.update("""
                    UPDATE marketing_events_db SET state = 'DELIVERED'
                    WHERE event_id = ? AND visitor_id = ? AND channel = 'BROWSER' AND state = 'PENDING'
                    """, eventId, id));
        }
    }

    @Transactional
    public Optional<Delivery> claim() {
        jdbc.update("""
                UPDATE marketing_events_db SET state = 'EXPIRED'
                WHERE state = 'PENDING' AND occurred_at < CURRENT_TIMESTAMP - INTERVAL '47 hours'
                """);
        jdbc.update("""
                UPDATE marketing_events_db e SET state = 'SUPPRESSED'
                WHERE e.state = 'PENDING' AND (NOT EXISTS (
                    SELECT 1 FROM marketing_visitors_db v WHERE v.id = e.visitor_id AND v.consent AND v.updated_at > CURRENT_TIMESTAMP - INTERVAL '90 days')
                  OR (e.channel = 'CAPI' AND NOT EXISTS (
                    SELECT 1 FROM payments_db p WHERE p.id = e.payment_id AND p.status = 'PAID'
                        AND p.is_active AND p.currency = e.currency AND p.amount >= e.cumulative_amount)))
                """);
        List<Delivery> found = jdbc.query("""
                SELECT e.*, mr.fbc, mr.fbp FROM marketing_events_db e
                JOIN marketing_registrations_db mr ON mr.registration_id = e.registration_id
                WHERE e.channel = 'CAPI' AND e.state = 'PENDING' AND e.test_mode = ? AND e.next_attempt_at <= CURRENT_TIMESTAMP
                    AND (e.lease_until IS NULL OR e.lease_until < CURRENT_TIMESTAMP)
                ORDER BY e.occurred_at, e.event_id LIMIT 1 FOR UPDATE OF e SKIP LOCKED
                """, (rs, row) -> new Delivery(rs.getObject("event_id", UUID.class), rs.getLong("tournament_id"),
                rs.getObject("visitor_id", UUID.class), rs.getTimestamp("occurred_at").toInstant(),
                rs.getBigDecimal("amount"), rs.getString("currency"), rs.getInt("paid_participants"),
                rs.getInt("attempts") + 1, rs.getString("fbc"), rs.getString("fbp"), rs.getString("action_source")), properties.testMode());
        return found.stream().findFirst().map(delivery -> {
            jdbc.update("UPDATE marketing_events_db SET lease_until = CURRENT_TIMESTAMP + INTERVAL '2 minutes', attempts = ? WHERE event_id = ?",
                    delivery.attempt(), delivery.eventId());
            return delivery;
        });
    }

    @Transactional
    public void finish(Delivery delivery, boolean accepted, int httpStatus) {
        boolean permanent = httpStatus >= 400 && httpStatus < 500 && httpStatus != 429 && httpStatus != 408;
        String state = accepted ? "DELIVERED" : permanent ? "FAILED" : "PENDING";
        int delay = Math.min(3600, 10 * (1 << Math.min(delivery.attempt(), 8)));
        jdbc.update("""
                UPDATE marketing_events_db SET state = ?, last_http_status = ?, lease_until = NULL,
                    next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
                WHERE event_id = ? AND attempts = ? AND state = 'PENDING'
                """, state, httpStatus, delay, delivery.eventId(), delivery.attempt());
    }
}
