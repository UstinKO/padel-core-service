package com.padle.core.padelcoreservice.marketing;

import com.padle.core.padelcoreservice.model.Payment;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketingJournal {
    private final JdbcTemplate jdbc;
    private final MetaProperties properties;
    private final MarketingContextService context;

    public void registration(TournamentRegistration registration) {
        Optional<UUID> visitor = properties.browserEnabled() ? context.currentVisitor() : Optional.empty();
        jdbc.update("""
                INSERT INTO marketing_registrations_db(registration_id, visitor_id, first_touch, last_touch,
                    expected_amount, expected_currency, fbc, fbp)
                SELECT r.id, v.id, v.first_touch, v.last_touch, t.precio, t.moneda, v.fbc, v.fbp
                FROM tournament_registrations_db r JOIN tournaments_db t ON t.id = r.tournament_id
                LEFT JOIN marketing_visitors_db v ON v.id = ? AND v.consent AND v.updated_at > CURRENT_TIMESTAMP - INTERVAL '90 days'
                WHERE r.id = ? ON CONFLICT DO NOTHING
                """, visitor.orElse(new UUID(0, 0)), registration.getId());
        if (!properties.browserEnabled() || !Boolean.TRUE.equals(registration.getIsActive())) {
            return;
        }
        MarketingPolicy.registrationEvent(registration.getStatus()).ifPresent(name -> {
            String key = "registration:" + registration.getId() + ":" + name;
            jdbc.update("""
                    INSERT INTO marketing_events_db(event_id, event_key, event_name, channel, test_mode,
                        registration_id, visitor_id, registration_status, tournament_id)
                    SELECT ?, ?, ?, 'BROWSER', ?, mr.registration_id, mr.visitor_id, ?, r.tournament_id
                    FROM marketing_registrations_db mr JOIN marketing_visitors_db v ON v.id = mr.visitor_id AND v.consent AND v.updated_at > CURRENT_TIMESTAMP - INTERVAL '90 days'
                    JOIN tournament_registrations_db r ON r.id = mr.registration_id
                    WHERE mr.registration_id = ? ON CONFLICT DO NOTHING
                    """, eventId(key), key, name, properties.testMode(), registration.getStatus().name(), registration.getId());
        });
    }

    public void payment(Payment payment) {
        jdbc.update("""
                INSERT INTO marketing_payment_progress_db(payment_id, currency) VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """, payment.getId(), payment.getCurrency());
        Progress previous = jdbc.queryForObject("""
                SELECT high_water, currency FROM marketing_payment_progress_db WHERE payment_id = ? FOR UPDATE
                """, (rs, row) -> new Progress(rs.getBigDecimal("high_water"), rs.getString("currency")), payment.getId());
        if (previous == null || payment.getStatus() != PaymentStatus.PAID || payment.getAmount() == null) {
            return;
        }
        BigDecimal amount = payment.getAmount().max(BigDecimal.ZERO);
        if (!previous.currency().equals(payment.getCurrency())) {
            jdbc.update("UPDATE marketing_payment_progress_db SET high_water = ?, currency = ? WHERE payment_id = ?",
                    amount, payment.getCurrency(), payment.getId());
            return;
        }
        BigDecimal delta = MarketingPolicy.positiveDelta(amount, previous.highWater());
        if (delta.signum() == 0) {
            return;
        }
        jdbc.update("UPDATE marketing_payment_progress_db SET high_water = ? WHERE payment_id = ?", amount, payment.getId());
        int participants = newlyPaidSlot(payment);
        if (properties.browserEnabled() && MarketingPolicy.supportedCurrency(payment.getCurrency())
                && Boolean.TRUE.equals(payment.getIsActive())) {
            purchase(payment, delta, participants);
        }
    }

    private void purchase(Payment payment, BigDecimal delta, int participants) {
        String key = "payment:" + payment.getId() + ":" + payment.getCurrency() + ":" + payment.getAmount().toPlainString();
        jdbc.update("""
                INSERT INTO marketing_events_db(event_id, event_key, event_name, channel, test_mode,
                    registration_id, payment_id, visitor_id, tournament_id, amount, cumulative_amount, currency, paid_participants, action_source)
                SELECT ?, ?, 'Purchase', 'CAPI', ?, mr.registration_id, ?, mr.visitor_id,
                    r.tournament_id, ?, ?, ?, ?, ?
                FROM marketing_registrations_db mr JOIN marketing_visitors_db v ON v.id = mr.visitor_id AND v.consent AND v.updated_at > CURRENT_TIMESTAMP - INTERVAL '90 days'
                JOIN tournament_registrations_db r ON r.id = mr.registration_id
                WHERE mr.registration_id = ? ON CONFLICT DO NOTHING
                """, eventId(key), key, properties.testMode(), payment.getId(), delta, payment.getAmount(), payment.getCurrency(),
                participants, MarketingPolicy.paymentSource(payment.getPaymentMethod()), payment.getRegistration().getId());
    }

    private int newlyPaidSlot(Payment payment) {
        if (!Boolean.TRUE.equals(payment.getIsActive())) {
            return 0;
        }
        boolean partner = Optional.ofNullable(payment.getNotes()).filter(value -> value.startsWith("PARTNER_PAYMENT")).isPresent();
        return jdbc.update("""
                INSERT INTO marketing_paid_slots_db(registration_id, partner_slot)
                SELECT mr.registration_id, ? FROM marketing_registrations_db mr
                WHERE mr.registration_id = ? AND mr.expected_amount > 0 AND mr.expected_currency = ?
                  AND (? + (SELECT COALESCE(SUM(p.amount), 0) FROM payments_db p
                    WHERE p.registration_id = mr.registration_id AND p.status = 'PAID' AND p.is_active
                      AND p.currency = mr.expected_currency AND p.id <> ?
                      AND (COALESCE(p.notes, '') LIKE 'PARTNER_PAYMENT%') = ?)) >= mr.expected_amount
                ON CONFLICT DO NOTHING
                """, partner, payment.getRegistration().getId(), payment.getCurrency(), payment.getAmount(), payment.getId(), partner);
    }

    static UUID eventId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private record Progress(BigDecimal highWater, String currency) { }
}
