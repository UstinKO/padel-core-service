package com.padle.core.application.registration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarketingEvents {
    List<BrowserEvent> browserEvents(UUID visitorId, boolean testMode);

    void acknowledge(UUID eventId, UUID visitorId);

    Optional<Delivery> claim(boolean testMode);

    void finish(Delivery delivery, boolean accepted, int httpStatus);

    record BrowserEvent(UUID eventId, String eventName, long tournamentId, String registrationStatus) { }
    record Delivery(UUID eventId, long tournamentId, UUID visitorId, Instant occurredAt,
                           BigDecimal amount, String currency, int paidParticipants, int attempt,
                           String fbc, String fbp, String actionSource) { }
}
