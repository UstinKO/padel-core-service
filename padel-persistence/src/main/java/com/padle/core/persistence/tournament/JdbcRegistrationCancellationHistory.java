package com.padle.core.persistence.tournament;

import com.padle.core.application.registration.CancellationHistory.Entry;
import com.padle.core.application.registration.RegistrationCancellationHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcRegistrationCancellationHistory implements RegistrationCancellationHistory {
    private static final String SELECT = """
        SELECT e.id AS event_id, e.tournament_id AS tournament_id,
               m.registration_id, m.version, e.cancelled_at, e.actor_kind AS initiator,
               e.scope, e.reason_code, e.organizer_comment
          FROM registration_cancellations e JOIN registration_cancellation_members m ON m.event_id=e.id
        """;

    private final JdbcClient jdbc;

    @Override
    public void insert(Event event) {
        jdbc.sql("""
                INSERT INTO registration_cancellations
                  (id,tournament_id,cancelled_at,actor_kind,actor_id,scope,reason_code,channel,organizer_comment)
                VALUES (:id,:tournament,:at,:actor,:actorId,:scope,:reason,:channel,:comment)
                """)
                .param("id", event.id()).param("tournament", event.tournamentId())
                .param("at", event.cancelledAt()).param("actor", event.actor().name())
                .param("actorId", event.actorId()).param("scope", event.scope().name())
                .param("reason", event.reason().name()).param("channel", event.channel().name())
                .param("comment", event.organizerComment()).update();
    }

    @Override
    public void member(UUID eventId, Member member) {
        jdbc.sql("""
                INSERT INTO registration_cancellation_members
                  (event_id,registration_id,version,player_id,previous_status,registration_date,pair_booking_id,partner_id)
                VALUES (:event,:id,:version,:player,:status,:registered,:booking,:partner)
                """)
                .param("event", eventId).param("id", member.registrationId()).param("version", member.version())
                .param("player", member.playerId()).param("status", member.previousStatus().name())
                .param("registered", member.registrationDate()).param("booking", member.pairBookingId())
                .param("partner", member.partnerId()).update();
    }

    @Override
    public List<Entry> forPlayer(long playerId, Collection<Long> tournamentIds) {
        if (tournamentIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + """
                WHERE (m.player_id=:player OR m.player_id IN (
                    SELECT guest_id FROM guest_profile_claims WHERE resolved_player_id=:player))
                  AND e.tournament_id IN (:events)
                ORDER BY e.recorded_at DESC,e.id DESC
                """).param("player", playerId).param("events", tournamentIds).query(Entry.class).list();
    }

    @Override
    public List<Entry> forManager(long tournamentId) {
        return jdbc.sql(SELECT + """
                WHERE e.tournament_id=:event ORDER BY e.recorded_at DESC,e.id DESC,m.registration_id
                """).param("event", tournamentId).query(Entry.class).list();
    }

    @Override
    public void saveReceipt(UUID eventId, ReceiptKey key, String body) {
        int changed = jdbc.sql("""
                UPDATE registration_cancellations SET receipt_actor=:actor,receipt_registration=:row,
                  receipt_version=:version,request_digest=:digest,receipt=CAST(:body AS jsonb)
                WHERE id=:event AND receipt IS NULL
                """).param("actor", key.actorId()).param("row", key.registrationId()).param("version", key.version())
                .param("digest", key.digest()).param("body", body).param("event", eventId).update();
        if (changed != 1) {
            throw new IllegalStateException("Результат отмены уже зафиксирован");
        }
    }

    @Override
    public Optional<String> receipt(ReceiptKey key) {
        return jdbc.sql("""
                SELECT receipt::text FROM registration_cancellations WHERE receipt_actor=:actor
                  AND receipt_registration=:row AND receipt_version=:version AND request_digest=:digest
                """).param("actor", key.actorId()).param("row", key.registrationId()).param("version", key.version())
                .param("digest", key.digest()).query(String.class).optional();
    }
}
