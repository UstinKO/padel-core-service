package com.padle.core.padelcoreservice.marketing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Cookie;

import java.util.Arrays;
import java.util.Optional;

public final class MarketingPolicy {

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

    public static String campaignLabel(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,100}") ? value : "";
    }

    public static Optional<String> publicPath(String path) {
        return Optional.ofNullable(path).filter(value -> value.matches(
                "/|/torneos|/clubs(?:/[0-9]+)?|/ranking|/torneo/[0-9]+|/tournaments/(?:americano|team-americano|team-playoff)/[0-9]+(?:/ranking)?|/tournaments/king-of-court/[0-9]+"));
    }
}
