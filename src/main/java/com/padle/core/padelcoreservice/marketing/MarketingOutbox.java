package com.padle.core.padelcoreservice.marketing;

import com.padle.core.application.registration.MarketingEvents;
import com.padle.core.application.registration.MarketingEvents.BrowserEvent;
import com.padle.core.application.registration.MarketingEvents.Delivery;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketingOutbox {
    private final MarketingEvents events;
    private final MetaProperties properties;
    private final MarketingContextService context;

    @Transactional(readOnly = true)
    public List<BrowserEvent> browserEvents(HttpServletRequest request) {
        if (!properties.browserEnabled() || !MarketingPolicy.hasConsent(request)) {
            return List.of();
        }
        return context.visitorId(request)
                .map(id -> events.browserEvents(id, properties.testMode()))
                .orElseGet(List::of);
    }

    @Transactional
    public void acknowledge(UUID eventId, HttpServletRequest request) {
        if (properties.browserEnabled() && MarketingPolicy.hasConsent(request)) {
            context.visitorId(request).ifPresent(id -> events.acknowledge(eventId, id));
        }
    }

    public Optional<Delivery> claim() {
        return events.claim(properties.testMode());
    }

    public void finish(Delivery delivery, boolean accepted, int httpStatus) {
        events.finish(delivery, accepted, httpStatus);
    }
}
