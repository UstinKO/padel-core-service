package com.padle.core.padelcoreservice.preview;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;

@Configuration(proxyBeanMethods = false)
@Profile("preview")
@EnableConfigurationProperties(PreviewProperties.class)
public class PreviewConfiguration {
    record DatabaseIdentity(boolean verified) { }

    @Bean
    static BeanFactoryPostProcessor previewMigrationBoundary() {
        return factory -> {
            if (factory.containsBeanDefinition("liquibase")) {
                var definition = factory.getBeanDefinition("liquibase");
                var existing = definition.getDependsOn();
                var dependencies = existing == null ? Stream.<String>empty() : Arrays.stream(existing);
                definition.setDependsOn(Stream.concat(dependencies, Stream.of("previewDatabaseIdentity")).toArray(String[]::new));
            }
        };
    }

    @Bean
    DatabaseIdentity previewDatabaseIdentity(DataSource source, PreviewProperties properties) throws SQLException {
        try (var connection = source.getConnection();
             var statement = connection.prepareStatement("SELECT instance_id FROM ui_preview.control WHERE current_database()='padel_ui_v2_preview'");
             var result = statement.executeQuery()) {
            if (!result.next() || !properties.instanceId().equals(result.getObject(1)) || result.next()) {
                throw new IllegalStateException("Маркер базы preview не совпадает");
            }
        }
        return new DatabaseIdentity(true);
    }

    @Bean
    ApplicationRunner previewSeed(PreviewFixtureService fixtures) {
        return arguments -> fixtures.seed();
    }

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> previewHeaders() {
        var filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");
                response.setHeader("X-Environment", "preview");
                if (request.getRequestURI().startsWith("/oauth2/") || request.getRequestURI().startsWith("/login/oauth2/")) {
                    response.sendError(404);
                    return;
                }
                chain.doFilter(request, response);
            }
        };
        var registration = new FilterRegistrationBean<OncePerRequestFilter>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
