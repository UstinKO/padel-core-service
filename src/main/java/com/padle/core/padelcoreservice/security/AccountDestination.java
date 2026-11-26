package com.padle.core.padelcoreservice.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AccountDestination {
    public static final String PARAMETER = "destination";
    private static final Pattern TOURNAMENT_MANAGEMENT = Pattern.compile(
            "/(?:admin/tournaments/[1-9][0-9]*"
                    + "|tournaments/team-playoff/admin/[1-9][0-9]*)");

    private AccountDestination() {}

    public static Optional<String> tournamentManagement(String path) {
        return Optional.ofNullable(path).filter(value -> TOURNAMENT_MANAGEMENT.matcher(value).matches());
    }

    public static Optional<String> safe(String value) {
        if (value == null || value.length() > 2048) return Optional.empty();
        String decoded = value;
        try {
            for (int round = 0; round < 5; round++) {
                if (!decoded.startsWith("/") || decoded.startsWith("//") || decoded.contains("\\")
                        || decoded.chars().anyMatch(c -> c <= 32 || c == 127)) return Optional.empty();
                var uri = URI.create(decoded);
                if (uri.isAbsolute() || uri.getRawAuthority() != null) return Optional.empty();
                var next = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
                if (next.equals(decoded)) return Optional.of(value);
                decoded = next;
            }
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public static Optional<String> requested(HttpServletRequest request) {
        return safe(request.getParameter(PARAMETER));
    }

    public static boolean telegramConnect(String value) {
        return value != null && value.matches("/telegram/connect(?:\\?request=[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})?");
    }

    public static Optional<String> authorized(HttpServletRequest request) {
        return request.getAttribute(PARAMETER) instanceof String value ? safe(value) : Optional.empty();
    }
}
