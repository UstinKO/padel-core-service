package com.padle.core.padelcoreservice.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public final class TournamentLogin {

    public static final String PARAMETER = "tournamentId";

    private TournamentLogin() {
    }

    public static Optional<Long> requestedTournament(HttpServletRequest request) {
        String value = request.getParameter(PARAMETER);
        if (value == null) {
            return Optional.empty();
        }
        try {
            long id = Long.parseLong(value);
            return id > 0 ? Optional.of(id) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<Long> authorizedTournament(HttpServletRequest request) {
        return request.getAttribute(PARAMETER) instanceof Long id && id > 0
                ? Optional.of(id) : Optional.empty();
    }

    public static String registrationUrl(long id) {
        return "/torneo/" + id + "#tournament-registration";
    }

    public static String failureUrl(String error, Optional<Long> tournament) {
        String url = "/login?error=" + error;
        return tournament.map(id -> url + "&" + PARAMETER + "=" + id).orElse(url);
    }
}
