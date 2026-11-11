package com.padle.core.padelcoreservice.preview;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreviewEnvironmentGuardTest {
    private final PreviewEnvironmentGuard guard = new PreviewEnvironmentGuard();

    @Test
    void isolatedProfileAcceptsOnlyItsDedicatedConfiguration() throws IOException {
        assertThatCode(() -> guard.postProcessEnvironment(environment(), null)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"spring.datasource.url,jdbc:postgresql://remote:5432/other",
            "spring.datasource.username,postgres", "spring.mail.host,smtp.example.test",
            "app.scheduling.enabled,true", "telegram.enabled,true", "app.auth.google-enabled,true",
            "logging.config,classpath:logback-spring.xml", "app.spa.routes[0],/torneos",
            "app.analytics.meta.access-token,sensitive-value", "app.preview.revision,unknown",
            "spring.liquibase.url,jdbc:postgresql://remote:5432/other",
            "spring.datasource.hikari.jdbc-url,jdbc:postgresql://remote:5432/other",
            "app.base-url,http://127.0.0.1/private password"})
    void unsafeOverridesAreRejectedWithoutLeakingTheirValues(String key, String value) throws IOException {
        var environment = environment();
        environment.getPropertySources().addFirst(new MapPropertySource("override", Map.of(key, value)));
        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(value);
    }

    @Test
    void mixedProductionProfileIsRejected() throws IOException {
        var environment = environment();
        environment.setActiveProfiles("preview", "prod");
        assertThatThrownBy(() -> guard.postProcessEnvironment(environment, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("profiles");
    }

    @Test
    void ordinaryProfilesRemainUntouched() {
        var environment = new StandardEnvironment();
        environment.setActiveProfiles("prod");
        assertThatCode(() -> guard.postProcessEnvironment(environment, null)).doesNotThrowAnyException();
    }

    private StandardEnvironment environment() throws IOException {
        var environment = new StandardEnvironment();
        environment.setActiveProfiles("preview");
        new YamlPropertySourceLoader().load("preview", new ClassPathResource("application-preview.yml"))
                .forEach(source -> environment.getPropertySources().addFirst(source));
        environment.getPropertySources().addFirst(new MapPropertySource("secrets", Map.of(
                "app.preview.instance-id", UUID.randomUUID().toString(), "app.preview.revision", "a".repeat(40),
                "app.preview.fixture-password", "fixture-password-".repeat(3),
                "spring.datasource.password", "database-password-".repeat(3),
                "jwt.secret", "jwt-secret-".repeat(5), "app.base-url", "http://127.0.0.1:18114")));
        return environment;
    }
}
