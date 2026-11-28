package com.padle.core.padelcoreservice.rating;

import com.padle.core.padelcoreservice.security.PlayerPrincipal;
import com.padle.core.padelcoreservice.util.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(EloPilotProperties.class)
public class EloPilotAccess {
    private final EloPilotProperties properties;

    @Value("${app.rating.pilot.all-players:false}")
    private boolean allPlayers;

    public boolean enabled() {
        return properties.enabled();
    }

    public boolean allows(Authentication authentication, Map<Long, Long> aliases) {
        return properties.enabled() && Optional.ofNullable(authentication)
                .filter(Authentication::isAuthenticated)
                .filter(auth -> auth.getAuthorities().stream().anyMatch(role -> "ROLE_PLAYER".equals(role.getAuthority())))
                .map(Authentication::getPrincipal)
                .flatMap(SecurityUtils::playerPrincipal)
                .map(PlayerPrincipal::getId)
                .map(id -> aliases.getOrDefault(id, id))
                .filter(id -> allPlayers || properties.playerIds().contains(id))
                .isPresent();
    }

    public void protectResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        response.addHeader("Vary", "Cookie");
        response.addHeader("Vary", "Authorization");
    }
}
