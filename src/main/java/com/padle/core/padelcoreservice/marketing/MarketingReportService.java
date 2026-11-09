package com.padle.core.padelcoreservice.marketing;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MarketingReportService {
    private final JdbcTemplate jdbc;

    public String export(long tournamentId) {
        String header = "registration_id,payment_id,partner_slot,registration_status,payment_status,amount,currency,fully_paid_slot,first_touch,last_touch,purchase_events,delivered_events\n";
        return header + jdbc.query("""
                SELECT r.id, p.id AS payment_id, (COALESCE(p.notes, '') LIKE 'PARTNER_PAYMENT%') AS partner_slot,
                    r.status AS registration_status, p.status AS payment_status, p.amount, p.currency,
                    (r.is_active AND r.status IN ('CONFIRMED', 'PARTICIPATED') AND p.is_active
                        AND mr.expected_amount > 0 AND mr.expected_currency = p.currency
                        AND (SELECT COALESCE(SUM(p2.amount), 0) FROM payments_db p2
                          WHERE p2.registration_id = r.id AND p2.status = 'PAID' AND p2.is_active
                            AND p2.currency = mr.expected_currency
                            AND (COALESCE(p2.notes, '') LIKE 'PARTNER_PAYMENT%') =
                                (COALESCE(p.notes, '') LIKE 'PARTNER_PAYMENT%')) >= mr.expected_amount) AS fully_paid,
                    mr.first_touch, mr.last_touch,
                    (SELECT COUNT(*) FROM marketing_events_db e WHERE e.payment_id = p.id AND e.channel = 'CAPI') AS purchase_events,
                    (SELECT COUNT(*) FROM marketing_events_db e WHERE e.payment_id = p.id AND e.state = 'DELIVERED' AND e.channel = 'CAPI') AS delivered_events
                FROM tournament_registrations_db r JOIN marketing_registrations_db mr ON mr.registration_id = r.id
                LEFT JOIN payments_db p ON p.registration_id = r.id
                WHERE r.tournament_id = ? ORDER BY r.id, p.id
                """, (rs, row) -> csv(rs.getString("id"), rs.getString("payment_id"), rs.getString("partner_slot"),
                rs.getString("registration_status"), rs.getString("payment_status"), rs.getString("amount"),
                rs.getString("currency"), rs.getString("fully_paid"), rs.getString("first_touch"),
                rs.getString("last_touch"), rs.getString("purchase_events"), rs.getString("delivered_events")), tournamentId)
                .stream().collect(Collectors.joining("\n"));
    }

    private String csv(String... values) {
        return Arrays.stream(values).map(value -> value == null ? "" : "\"" + value.replace("\"", "\"\"") + "\"")
                .collect(Collectors.joining(","));
    }
}
