package com.padle.core.padelcoreservice.config.spa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpaPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void missingPropertyUsesSafeEmptyRoutes() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(SpaProperties.class).routes()).isEmpty();
        });
    }

    @Test
    void emptyListUsesSafeEmptyRoutes() {
        contextRunner.withPropertyValues("app.spa.routes=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SpaProperties.class).routes()).isEmpty();
                });
    }

    @Test
    void validPatternsAreDeduplicatedAndImmutable() {
        contextRunner.withPropertyValues(
                        "app.spa.routes[0]=/react-pilot/**",
                        "app.spa.routes[1]=/react-pilot/**",
                        "app.spa.routes[2]=/clubs/{id}/react"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var routes = context.getBean(SpaProperties.class).routes();
                    assertThat(routes).containsExactly("/react-pilot/**", "/clubs/{id}/react");
                    assertThatThrownBy(() -> routes.add("/other"))
                            .isInstanceOf(UnsupportedOperationException.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            " ", "relative/**", "/**", "/{*path}",
            "/api/**", "/api/pilot/**", "/app/**", "/web/**", "/preview/**", "/logout",
            "/login/{segment}/callback", "/{segment}/**", "/{segment}/pilot"
    })
    void unsafePatternStopsContextStartup(String route) {
        contextRunner.withPropertyValues("app.spa.routes[0]=" + route)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SpaProperties.class)
    static class PropertiesConfig {
    }
}
