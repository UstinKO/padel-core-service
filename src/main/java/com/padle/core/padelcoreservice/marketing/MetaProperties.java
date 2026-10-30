package com.padle.core.padelcoreservice.marketing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("app.analytics.meta")
public record MetaProperties(boolean enabled, String pixelId, String accessToken,
                             String apiVersion, boolean testMode, String testEventCode) {
    public boolean browserEnabled() {
        return enabled && pixelId != null && pixelId.matches("[0-9]{5,30}");
    }

    public boolean serverEnabled() {
        return browserEnabled() && accessToken != null && !accessToken.isBlank()
                && apiVersion != null && apiVersion.matches("v[0-9]{1,3}\\.[0-9]{1,2}")
                && (!testMode || testEventCode != null && !testEventCode.isBlank());
    }

    @Override
    public String toString() {
        return "MetaProperties[enabled=" + enabled + ", testMode=" + testMode + "]";
    }

    @Configuration
    @EnableConfigurationProperties(MetaProperties.class)
    public static class Binding { }
}
