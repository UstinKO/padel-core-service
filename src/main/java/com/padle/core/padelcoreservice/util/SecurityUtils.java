package com.padle.core.padelcoreservice.util;

import com.padle.core.padelcoreservice.security.PlayerPrincipal;

import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<PlayerPrincipal> playerPrincipal(Object principal) {
        return principal instanceof PlayerPrincipal player ? Optional.of(player) : Optional.empty();
    }
}
