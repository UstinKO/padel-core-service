package com.padle.core.padelcoreservice.receipts;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("telegram.players.receipts")
public record ReceiptProperties(@DefaultValue("false") boolean enabled,
                                @DefaultValue("true") boolean pilotEnabled,
                                Long pilotPlayerId, Long pilotTournamentId) {
    @Configuration
    @EnableConfigurationProperties(ReceiptProperties.class)
    static class Binding {}
}
