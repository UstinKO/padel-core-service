package com.padle.core.padelcoreservice.security;

import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TelegramOnboardingSession {
    public static final String KEY = "telegramOnboardingRequests";
    private static final int MAX_REQUESTS = 8;

    private TelegramOnboardingSession() {}

    public static void remember(HttpSession session, UUID id, String token) {
        synchronized (session) {
            var entries = entries(session);
            entries.remove(id.toString());
            entries.put(id.toString(), token);
            while (entries.size() > MAX_REQUESTS) entries.remove(entries.keySet().iterator().next());
            session.setAttribute(KEY, entries);
        }
    }

    public static String token(HttpSession session, String id) {
        synchronized (session) { return entries(session).getOrDefault(id, ""); }
    }

    public static String destination(UUID id) { return "/telegram/connect?request=" + id; }

    private static LinkedHashMap<String, String> entries(HttpSession session) {
        var result = new LinkedHashMap<String, String>();
        if (session.getAttribute(KEY) instanceof Map<?, ?> saved) {
            saved.forEach((key, value) -> {
                if (key instanceof String id && value instanceof String token) result.put(id, token);
            });
        }
        return result;
    }
}
