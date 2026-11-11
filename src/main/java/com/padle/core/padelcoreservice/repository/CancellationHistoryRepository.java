package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.dto.CancellationHistory.*;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.service.cancellation.CancellationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CancellationHistoryRepository {
    private final JdbcClient jdbc;
    public record Row(UUID eventId, long tournamentId, long registrationId, long version,
                      LocalDateTime cancelledAt, ActorKind initiator, Scope scope, Reason reasonCode,
                      String organizerComment) {}
    private static final String SELECT = """
        SELECT e.id AS event_id, e.tournament_id AS tournament_id,
               m.registration_id, m.version, e.cancelled_at, e.actor_kind AS initiator,
               e.scope, e.reason_code, e.organizer_comment
          FROM registration_cancellations e JOIN registration_cancellation_members m ON m.event_id=e.id
        """;
    public void insert(UUID id, LocalDateTime at, CancellationContext context, TournamentRegistration row) {
        jdbc.sql("""
            INSERT INTO registration_cancellations
              (id,tournament_id,cancelled_at,actor_kind,actor_id,scope,reason_code,channel,organizer_comment)
            VALUES (:id,:event,:at,:actor,:actorId,:scope,:reason,:channel,:comment)
            """).param("id",id).param("event",row.getTournament().getId()).param("at",at)
            .param("actor",context.initiator().name()).param("actorId",context.actorId())
            .param("scope",context.scope().name()).param("reason",context.reason().name())
            .param("channel",context.channel().name()).param("comment",context.organizerComment()).update();
    }
    public void member(UUID event, TournamentRegistration row) {
        jdbc.sql("""
            INSERT INTO registration_cancellation_members
              (event_id,registration_id,version,player_id,previous_status,registration_date,pair_booking_id,partner_id)
            VALUES (:event,:id,:version,:player,:status,:registered,:booking,:partner)
            """).param("event",event).param("id",row.getId()).param("version",row.getCancellationVersion()+1)
            .param("player",row.getPlayer().getId()).param("status",row.getStatus().name())
            .param("registered",row.getRegistrationDate()).param("booking",row.getPairBookingId())
            .param("partner",row.getPartner()==null ? null : row.getPartner().getId()).update();
    }
    public List<Row> forPlayer(long playerId, Collection<Long> eventIds) {
        if(eventIds.isEmpty()) return List.of();
        return jdbc.sql(SELECT+" WHERE m.player_id=:player AND e.tournament_id IN (:events) ORDER BY e.recorded_at DESC,e.id DESC")
                .param("player",playerId).param("events",eventIds).query(Row.class).list();
    }
    public List<Row> forManager(long tournamentId) {
        return jdbc.sql(SELECT+" WHERE e.tournament_id=:event ORDER BY e.recorded_at DESC,e.id DESC,m.registration_id")
                .param("event",tournamentId).query(Row.class).list();
    }
    public void receipt(UUID event, TournamentRegistration row, long actorId, String digest, String body) {
        int changed = jdbc.sql("""
            UPDATE registration_cancellations SET receipt_actor=:actor,receipt_registration=:row,
              receipt_version=:version,request_digest=:digest,receipt=CAST(:body AS jsonb) WHERE id=:event AND receipt IS NULL
            """).param("actor",actorId).param("row",row.getId()).param("version",row.getCancellationVersion())
            .param("digest",digest).param("body",body).param("event",event).update();
        if(changed!=1) throw new IllegalStateException("Результат отмены уже зафиксирован");
    }
    public Optional<String> receipt(TournamentRegistration row,long actorId,String digest) {
        return jdbc.sql("""
            SELECT receipt::text FROM registration_cancellations WHERE receipt_actor=:actor
              AND receipt_registration=:row AND receipt_version=:version AND request_digest=:digest
            """).param("actor",actorId).param("row",row.getId()).param("version",row.getCancellationVersion())
            .param("digest",digest).query(String.class).optional();
    }
}
