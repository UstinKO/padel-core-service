package com.padle.core.padelcoreservice.service.marketing;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class QrRequestPolicy {
    private static final List<String> CRAWLERS = List.of("bot", "spider", "crawler", "facebookexternalhit",
            "whatsapp", "slack", "discord", "preview", "headless", "curl/", "wget/");

    private QrRequestPolicy() {}

    public static boolean shouldCount(String method, String userAgent, String purpose, String secPurpose) {
        String agent = Objects.requireNonNullElse(userAgent, "").toLowerCase(Locale.ROOT);
        String intent = (Objects.requireNonNullElse(purpose, "") + " "
                + Objects.requireNonNullElse(secPurpose, "")).toLowerCase(Locale.ROOT);
        return "GET".equals(method) && !intent.contains("prefetch") && !intent.contains("preview")
                && CRAWLERS.stream().noneMatch(agent::contains);
    }
}
