package com.padle.core.padelcoreservice.config.spa;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseConfigurationTest {
    @Test
    void managedReleaseRejectsPublicSpaCutover() {
        runner(List.of("/torneos")).withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasFailed());
        runner(List.of("/players/dashboard", "/torneos")).withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasFailed());
        runner(List.of("/players/dashboard")).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void managedProductionReleaseAcceptsOnlyTheApprovedCabinet() {
        runner(List.of("/players/dashboard")).withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void verifiedRevisionIsAvailableToSmokeWithoutExposingConfiguration() {
        runner(List.of()).run(context -> {
            assertThat(context).hasNotFailed();
            var registration = context.getBean(FilterRegistrationBean.class);
            var response = new MockHttpServletResponse();
            registration.getFilter().doFilter(new MockHttpServletRequest(), response, (request, target) -> {});
            assertThat(response.getHeader("X-Release-Revision")).isEqualTo("a".repeat(40));
        });
    }

    @Test
    void arbitraryFilesystemLocationsAndInvalidRevisionAreRejected() {
        assertThatThrownBy(() -> new ReleaseProperties(true, "bad", "/opt/padel-retained-assets"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReleaseProperties(true, "a".repeat(40), "/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ApplicationContextRunner runner(List<String> routes) {
        return new ApplicationContextRunner().withUserConfiguration(ReleaseConfiguration.class)
                .withPropertyValues("app.release.enabled=true")
                .withBean(SpaProperties.class, () -> new SpaProperties(routes))
                .withBean(ReleaseProperties.class, () -> new ReleaseProperties(true, "a".repeat(40), "/opt/padel-retained-assets"));
    }
}
