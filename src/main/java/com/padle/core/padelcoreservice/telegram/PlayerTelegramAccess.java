package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlayerTelegramAccess {
    private final PlayerTelegramProperties properties;

    public boolean pilot() {
        return properties.pilotEnabled();
    }

    public boolean allowsPlayer(Long playerId) {
        return properties.enabled() && playerId != null && playerId > 0
                && (!pilot() || playerId.equals(properties.pilotPlayerId()));
    }

    public boolean allowsEvent(Long tournamentId) {
        return properties.enabled() && tournamentId != null && tournamentId > 0
                && (!pilot() || tournamentId.equals(properties.pilotTournamentId()));
    }
}
