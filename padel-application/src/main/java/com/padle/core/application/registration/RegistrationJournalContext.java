package com.padle.core.application.registration;

import java.util.Optional;
import java.util.UUID;

public interface RegistrationJournalContext {
    boolean browserEnabled();

    boolean testMode();

    Optional<UUID> currentVisitor();

    void registrationRecorded(long registrationId);
}
