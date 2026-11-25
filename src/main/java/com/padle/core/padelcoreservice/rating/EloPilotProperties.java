package com.padle.core.padelcoreservice.rating;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties("app.rating.pilot")
public record EloPilotProperties(boolean enabled, Set<Long> playerIds, Path snapshot) {
    public EloPilotProperties {
        playerIds = Set.copyOf(Objects.requireNonNullElse(playerIds, Set.of()));
        if (playerIds.stream().anyMatch(id -> id <= 0)) {
            throw new IllegalArgumentException("ID участников пилота должны быть положительными");
        }
    }
}
