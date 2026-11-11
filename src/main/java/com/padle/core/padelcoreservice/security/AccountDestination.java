package com.padle.core.padelcoreservice.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class AccountDestination {
    public static final String PARAMETER = "destination";
    private AccountDestination() {}

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

    public static Optional<String> authorized(HttpServletRequest request) {
        return request.getAttribute(PARAMETER) instanceof String value ? safe(value) : Optional.empty();
    }
}
