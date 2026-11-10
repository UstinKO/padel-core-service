package com.padle.core.padelcoreservice.config.spa;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SpaProperties.class)
public class SpaWebConfig implements WebMvcConfigurer {

    private static final List<String> PREVIEW_ROUTES = List.of("/preview", "/preview/{*path}");

    @Bean
    SpaForwardHandlerMapping spaForwardHandlerMapping(SpaProperties properties) {
        var routes = Stream.concat(properties.routes().stream(), PREVIEW_ROUTES.stream()).toList();
        var mapping = new SpaForwardHandlerMapping(routes);
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return mapping;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/web/assets/**")
                .addResourceLocations("classpath:/static/web/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/web/**")
                .addResourceLocations("classpath:/static/web/")
                .setCacheControl(CacheControl.noCache());
    }
}
