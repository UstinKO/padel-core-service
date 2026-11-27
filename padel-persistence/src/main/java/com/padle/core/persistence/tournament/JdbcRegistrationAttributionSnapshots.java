package com.padle.core.persistence.tournament;

import com.padle.core.application.registration.RegistrationAttributionSnapshots;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcRegistrationAttributionSnapshots implements RegistrationAttributionSnapshots {
    private final JdbcTemplate jdbc;

    @Override
    public void record(long registrationId, long actorId, UUID visitorId, Instant recordedAt) {
        Timestamp timestamp = Timestamp.from(recordedAt);
        jdbc.update("""
                UPDATE marketing_registrations_db mr SET
                    attribution_visitor_id = v.id,
                    acquisition_player_id = a.player_id,
                    acquisition_touch = CASE WHEN r.player_id = ? THEN a.first_touch END,
                    registration_touch = CASE WHEN (v.last_touch::jsonb->>'capturedAt')::timestamptz
                        BETWEEN ?::timestamptz - INTERVAL '90 days' AND ?::timestamptz THEN v.last_touch END,
                    attribution_recorded_at = ?,
                    attribution_expires_at = COALESCE(a.expires_at, v.expires_at)
                FROM tournament_registrations_db r
                LEFT JOIN marketing_attribution_visitors_db v ON v.id = ? AND v.consent
                    AND v.expires_at >= CURRENT_TIMESTAMP AND v.bound_player_id = ?
                LEFT JOIN marketing_acquisition_db a ON a.player_id = r.player_id
                    AND a.player_id = ? AND a.expires_at >= CURRENT_TIMESTAMP
                WHERE mr.registration_id = r.id AND r.id = ? AND mr.attribution_recorded_at IS NULL
                """, actorId, timestamp, timestamp, timestamp, visitorId, actorId, actorId, registrationId);
    }
}
