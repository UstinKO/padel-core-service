package com.padle.core.padelcoreservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("telegram.players")
public record PlayerTelegramProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("") String botUsername,
        @DefaultValue("false") boolean automaticEnabled,
        @DefaultValue("24") int confirmationHours,
        @DefaultValue("3") int reminderHours,
        Long organizerOwnerId, Long organizerChatId,
        @DefaultValue("false") boolean dailySummaryEnabled,
        @DefaultValue("") String botToken) {
    public PlayerTelegramProperties {
        if (confirmationHours < 1 || confirmationHours > 72 || reminderHours < 1
                || reminderHours >= confirmationHours) {
            throw new IllegalArgumentException("Недопустимые интервалы Telegram-напоминаний");
        }
    }

    @Configuration
    @EnableConfigurationProperties(PlayerTelegramProperties.class)
    static class Registration {}
}
