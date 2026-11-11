package com.padle.core.padelcoreservice.preview;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Проверяет изоляцию до создания datasource, запуска Liquibase и внешних клиентов. */
public class PreviewEnvironmentGuard implements EnvironmentPostProcessor, Ordered {
    private static final Map<String, String> REQUIRED = Map.ofEntries(
            Map.entry("spring.datasource.url", "jdbc:postgresql://preview-db:5432/padel_ui_v2_preview"),
            Map.entry("spring.datasource.username", "preview"),
            Map.entry("spring.mail.host", "preview-mail"), Map.entry("spring.mail.port", "1025"),
            Map.entry("logging.config", "classpath:logback-preview.xml"),
            Map.entry("app.scheduling.enabled", "false"), Map.entry("telegram.enabled", "false"),
            Map.entry("app.auth.google-enabled", "false"), Map.entry("app.analytics.meta.enabled", "false"),
            Map.entry("recaptcha.enabled", "false"), Map.entry("logging.loki.enabled", "false"),
            Map.entry("disposable-email-domains.checkers.daily-updated-domains", "false"),
            Map.entry("disposable-email-domains.checkers.reload.enabled", "false"),
            Map.entry("spring.security.oauth2.client.registration.google.client-id", "preview-disabled"),
            Map.entry("spring.security.oauth2.client.registration.google.client-secret", "preview-disabled"));
    private static final List<String> EMPTY = List.of("telegram.bot-token", "telegram.chat-id",
            "app.analytics.meta.access-token", "app.analytics.meta.pixel-id", "app.analytics.ga4-id",
            "app.analytics.clarity-id", "pipeline.shared-secret", "spring.mail.username", "spring.mail.password",
            "spring.liquibase.url", "spring.liquibase.user", "spring.liquibase.password",
            "spring.datasource.hikari.jdbc-url", "spring.datasource.hikari.data-source-class-name");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("preview")) {
            return;
        }
        require(Set.copyOf(Arrays.asList(environment.getActiveProfiles())).equals(Set.of("preview")), "profiles");
        REQUIRED.forEach((key, expected) -> require(expected.equals(environment.getProperty(key)), key));
        EMPTY.forEach(key -> require(environment.getProperty(key, "").isEmpty(), key));
        require(Binder.get(environment).bind("app.spa.routes", Bindable.listOf(String.class)).orElse(List.of()).isEmpty(), "app.spa.routes");
        var properties = Binder.get(environment).bind("app.preview", PreviewProperties.class).orElseThrow(
                () -> new IllegalStateException("Отсутствует конфигурация preview"));
        require(properties.instanceId() != null, "instance-id");
        require(properties.revision() != null && properties.revision().matches("[0-9a-f]{40}"), "revision");
        require(properties.fixturePassword() != null && properties.fixturePassword().length() >= 24, "fixture-password");
        require(environment.getProperty("spring.datasource.password", "").length() >= 24, "database-password");
        require(environment.getProperty("jwt.secret", "").length() >= 43, "jwt-secret");
        var origin = origin(environment.getProperty("app.base-url", ""));
        require("http".equals(origin.getScheme()) && "127.0.0.1".equals(origin.getHost())
                && origin.getUserInfo() == null && origin.getQuery() == null && origin.getFragment() == null
                && Set.of("", "/").contains(origin.getPath()) && origin.getPort() >= 1024, "base-url");
    }

    private static URI origin(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Небезопасная конфигурация preview: base-url");
        }
    }

    private static void require(boolean valid, String key) {
        if (!valid) {
            throw new IllegalStateException("Небезопасная конфигурация preview: " + key);
        }
    }

    @Override
    public int getOrder() { return ConfigDataEnvironmentPostProcessor.ORDER + 1; }
}
