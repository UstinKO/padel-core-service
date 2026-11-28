package com.padle.core.padelcoreservice.marketing;

import com.padle.core.application.registration.RegistrationJournalContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class RequestRegistrationJournalContext implements RegistrationJournalContext {
    private final MetaProperties properties;
    private final MarketingContextService context;
    private final AttributionRegistrationJournal attribution;

    @Override
    public boolean browserEnabled() {
        return properties.browserEnabled();
    }

    @Override
    public boolean testMode() {
        return properties.testMode();
    }

    @Override
    public Optional<UUID> currentVisitor() {
        return context.currentVisitor();
    }

    @Override
    public void registrationRecorded(long registrationId) {
        attribution.registration(registrationId);
    }
}
