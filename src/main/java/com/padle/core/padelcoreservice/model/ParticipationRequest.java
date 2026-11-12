package com.padle.core.padelcoreservice.model;

import com.padle.core.padelcoreservice.model.enums.ParticipationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "participation_requests_db")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParticipationRequest {
    public enum Trigger { RESERVE, REPLACEMENT }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private TournamentRegistration registration;

    private long registrationVersion;
    private Long delegatePlayerId;

    @Column(name = "trigger_kind")
    @Enumerated(EnumType.STRING)
    private Trigger trigger;

    @Column(name = "participation_status")
    @Enumerated(EnumType.STRING)
    private ParticipationStatus status;

    private OffsetDateTime requestedAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime respondedAt;
    private Long respondedBy;
    private OffsetDateTime supersededAt;

    public ParticipationRequest(TournamentRegistration registration, Long delegatePlayerId, Trigger trigger,
                                OffsetDateTime now) {
        this.id = UUID.randomUUID();
        this.registration = registration;
        this.registrationVersion = registration.getCancellationVersion();
        this.delegatePlayerId = delegatePlayerId;
        this.trigger = trigger;
        this.status = ParticipationStatus.PENDING;
        this.requestedAt = now;
        this.expiresAt = now.plusDays(7);
    }

    public void respond(ParticipationStatus decision, long actorId, OffsetDateTime now) {
        status = decision;
        respondedBy = actorId;
        respondedAt = now;
    }

    public void supersede(OffsetDateTime now) {
        supersededAt = now;
    }
}
