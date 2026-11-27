package com.padle.core.application.registration;

import java.time.Instant;
import java.util.UUID;

public interface RegistrationAttributionSnapshots {
    void record(long registrationId, long actorId, UUID visitorId, Instant recordedAt);
}
