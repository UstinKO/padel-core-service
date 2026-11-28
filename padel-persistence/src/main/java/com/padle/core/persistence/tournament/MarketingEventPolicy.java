package com.padle.core.persistence.tournament;

import com.padle.core.application.registration.PaymentMethod;
import com.padle.core.application.registration.RegistrationStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

final class MarketingEventPolicy {
    private static final Set<String> CURRENCIES = Set.of("ARS", "USD", "EUR", "BRL", "UYU", "CLP", "PYG", "BOB", "PEN", "COP", "MXN", "GBP", "CAD", "AUD", "CHF", "JPY");

    private MarketingEventPolicy() {
    }

    static Optional<String> registrationEvent(RegistrationStatus status) {
        if (status == null) {
            return Optional.empty();
        }
        return switch (status) {
            case CONFIRMED, PARTICIPATED -> Optional.of("EventRegistration");
            case WAITLIST -> Optional.of("EventWaitlist");
            case PENDING_PARTNER, PARTNER_INVITED, PAIR_REGISTERED, SOLO_ADD_LATER, SOLO_SEARCH -> Optional.of("EventRegistrationPending");
            case CANCELLED, SOLO_EXPIRED -> Optional.empty();
        };
    }

    static boolean supportedCurrency(String currency) {
        return CURRENCIES.contains(currency == null ? "" : currency);
    }

    static String paymentSource(PaymentMethod method) {
        return method == PaymentMethod.CASH ? "physical_store" : "other";
    }

    static BigDecimal positiveDelta(BigDecimal amount, BigDecimal highWater) {
        return amount == null ? BigDecimal.ZERO : amount.subtract(highWater).max(BigDecimal.ZERO);
    }
}
