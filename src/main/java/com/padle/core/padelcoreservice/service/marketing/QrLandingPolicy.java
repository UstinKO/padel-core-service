package com.padle.core.padelcoreservice.service.marketing;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class QrLandingPolicy {
    public static final List<String> SOURCES = List.of("black_padel", "15cero_villa_crespo", "15cero_saavedra");
    public static final List<String> REPORT_SOURCES = List.of("black_padel", "15cero_villa_crespo", "15cero_saavedra", "reby");

    private QrLandingPolicy() {}

    public static Optional<String> source(Map<String, String[]> parameters) {
        if (single(parameters, "utm_medium").filter("qr"::equals).isEmpty()
                || single(parameters, "utm_campaign").filter("offline_tournaments"::equals).isEmpty()) {
            return Optional.empty();
        }
        // Reby уже учитывается на redirect, его главная с UTM повторно не считается.
        return single(parameters, "utm_source").filter(SOURCES::contains);
    }

    private static Optional<String> single(Map<String, String[]> parameters, String name) {
        return Optional.ofNullable(parameters.get(name)).filter(values -> values.length == 1)
                .map(values -> values[0]);
    }
}
