package com.padle.core.padelcoreservice.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public final class CancellationHistory {
    private CancellationHistory() {}
    public enum ActorKind { PLAYER, OWNER, SYSTEM, UNKNOWN }
    public enum Scope { PARTICIPANT, PAIR }
    public enum Reason { PARTICIPANT_WITHDRAWAL, PAIR_WITHDRAWAL, ORGANIZER_REMOVAL,
        PLAYER_DEACTIVATED, PARTICIPANT_REPLACED, LEGACY_UNKNOWN }
    public enum Channel { ACCOUNT, ORGANIZER, RESERVE_LINK, REPLACEMENT, SYSTEM, LEGACY }
    public record Entry(UUID eventId, long tournamentId, long registrationId, long version,
                        LocalDateTime cancelledAt, ActorKind initiator, Scope scope, Reason reasonCode,
                        String organizerComment) {}
    public record Result(boolean changed, UUID eventId, LocalDateTime cancelledAt) {}
}
