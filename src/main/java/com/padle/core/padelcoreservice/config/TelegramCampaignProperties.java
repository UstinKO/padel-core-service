package com.padle.core.padelcoreservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("telegram.players.campaigns")
public record TelegramCampaignProperties(@DefaultValue("false") boolean enabled) {
    @Configuration
    @EnableConfigurationProperties(TelegramCampaignProperties.class)
    static class Registration {}
}
