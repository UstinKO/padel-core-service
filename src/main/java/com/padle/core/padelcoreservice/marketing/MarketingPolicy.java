package com.padle.core.padelcoreservice.marketing;

import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.PaymentMethod;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public final class MarketingPolicy {
    private static final Set<String> CURRENCIES = Set.of("ARS", "USD", "EUR", "BRL", "UYU", "CLP", "PYG", "BOB", "PEN", "COP", "MXN", "GBP", "CAD", "AUD", "CHF", "JPY");

    private MarketingPolicy() { }

    public static boolean hasConsent(HttpServletRequest request) {
        String consent = cookie(request, "cookieConsent").orElse("");
        return consent.equals("accepted") || consent.equals("customized")
                && cookie(request, "marketingConsent").filter("true"::equals).isPresent();
    }

    public static Optional<String> cookie(HttpServletRequest request, String name) {
        return Optional.ofNullable(request.getCookies()).stream().flatMap(Arrays::stream)
                .filter(cookie -> cookie.getName().equals(name))
                .map(Cookie::getValue).findFirst();
    }

    public static Optional<String> registrationEvent(RegistrationStatus status) {
        if (status == null) {
            return Optional.empty();
        }
        return switch (status) {
            case CONFIRMED, PARTICIPATED -> Optional.of("EventRegistration");
            case WAITLIST, WAITLIST_INVITED -> Optional.of("EventWaitlist");
            case PENDING_PARTNER, PARTNER_INVITED, PAIR_REGISTERED, SOLO_ADD_LATER, SOLO_SEARCH -> Optional.of("EventRegistrationPending");
            case CANCELLED, SOLO_EXPIRED -> Optional.empty();
        };
    }

    public static boolean supportedCurrency(String currency) {
        return CURRENCIES.contains(currency == null ? "" : currency);
    }

    public static String paymentSource(PaymentMethod method) {
        return method == PaymentMethod.CASH ? "physical_store" : "other";
    }

    public static BigDecimal positiveDelta(BigDecimal amount, BigDecimal highWater) {
        return amount == null ? BigDecimal.ZERO : amount.subtract(highWater).max(BigDecimal.ZERO);
    }

    public static String campaignLabel(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,100}") ? value : "";
    }

    public static Optional<String> publicPath(String path) {
        return Optional.ofNullable(path).filter(value -> value.matches(
                "/|/torneos|/clubs(?:/[0-9]+)?|/ranking|/torneo/[0-9]+|/tournaments/(?:americano|team-americano|team-playoff)/[0-9]+(?:/ranking)?|/tournaments/king-of-court/[0-9]+"));
    }
}
