package com.padle.core.padelcoreservice.config.spa;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.release.enabled", havingValue = "true")
public class ReleaseConfiguration {
    public ReleaseConfiguration(SpaProperties routes, Environment environment) {
        if (routes.routes().isEmpty()) return;
        if (!environment.matchesProfiles("prod") || !routes.routes().equals(List.of("/players/dashboard"))) {
            throw new IllegalStateException("Выпуск UI v2 содержит маршрут без подтверждённой приёмки");
        }
    }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> releaseRevisionHeader(ReleaseProperties properties) {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws IOException, ServletException {
                response.setHeader("X-Release-Revision", properties.revision());
                chain.doFilter(request, response);
            }
        };
        return new FilterRegistrationBean<>(filter);
    }
}
