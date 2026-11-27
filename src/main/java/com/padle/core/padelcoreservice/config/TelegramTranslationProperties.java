package com.padle.core.padelcoreservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("telegram.players.translation")
public record TelegramTranslationProperties(@DefaultValue("false") boolean enabled,
        @DefaultValue("") String apiKey, @DefaultValue("gpt-6.1-terra") String model) {
    @Override public String toString() {
        return "TelegramTranslationProperties[enabled=" + enabled + ", apiKey=***, model=" + model + "]";
    }
    @Configuration
    @EnableConfigurationProperties(TelegramTranslationProperties.class)
    static class Registration {}
}
