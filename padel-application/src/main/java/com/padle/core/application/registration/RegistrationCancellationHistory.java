package com.padle.core.application.registration;

import com.padle.core.application.registration.CancellationHistory.ActorKind;
import com.padle.core.application.registration.CancellationHistory.Channel;
import com.padle.core.application.registration.CancellationHistory.Entry;
import com.padle.core.application.registration.CancellationHistory.Reason;
import com.padle.core.application.registration.CancellationHistory.Scope;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationCancellationHistory {
    void insert(Event event);

    void member(UUID eventId, Member member);

    List<Entry> forPlayer(long playerId, Collection<Long> tournamentIds);

    List<Entry> forManager(long tournamentId);

    void saveReceipt(UUID eventId, ReceiptKey key, String body);

    Optional<String> receipt(ReceiptKey key);

    record Event(UUID id, long tournamentId, LocalDateTime cancelledAt, ActorKind actor, Long actorId,
                 Scope scope, Reason reason, Channel channel, String organizerComment) {
    }

    record Member(long registrationId, long version, long playerId, RegistrationStatus previousStatus,
                  LocalDateTime registrationDate, UUID pairBookingId, Long partnerId) {
    }

    record ReceiptKey(long registrationId, long version, long actorId, String digest) {
    }
}
