package com.padle.core.padelcoreservice.config.spa;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.padle.core.padelcoreservice.rating.EloPilotAccess;
import com.padle.core.padelcoreservice.rating.PilotEloRanking;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({SpaProperties.class, ReleaseProperties.class})
@RequiredArgsConstructor
public class SpaWebConfig implements WebMvcConfigurer {
    private final ReleaseProperties release;

    private static final List<String> PREVIEW_ROUTES = List.of("/preview", "/preview/{*path}");

    @Bean
    SpaForwardHandlerMapping spaForwardHandlerMapping(SpaProperties properties, SpaPageService pages,
            ObjectProvider<PilotEloRanking> pilot, EloPilotAccess access) {
        var routes = Stream.concat(properties.routes().stream(), PREVIEW_ROUTES.stream()).toList();
        var mapping = new SpaForwardHandlerMapping(routes, (request, response) -> {
            if (dashboard(request)) {
                access.protectResponse(response);
            }
            if (!pages.render(request, response)) {
                request.getRequestDispatcher("/web/index.html").forward(request, response);
            }
        }, request -> dashboard(request) && pilot.stream().anyMatch(
                ranking -> ranking.allows(SecurityContextHolder.getContext().getAuthentication())));
        mapping.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return mapping;
    }

    private static boolean dashboard(jakarta.servlet.http.HttpServletRequest request) {
        return ServletRequestPathUtils.getParsedRequestPath(request).pathWithinApplication().value()
                .equals("/players/dashboard");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        var assets = registry.addResourceHandler("/web/assets/**")
                .addResourceLocations("classpath:/static/web/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        if (release.enabled()) {
            var retained = Path.of(release.assetsDirectory()).toUri().toString();
            assets.addResourceLocations(retained.endsWith("/") ? retained : retained + "/");
        }
        registry.addResourceHandler("/web/**")
                .addResourceLocations("classpath:/static/web/")
                .setCacheControl(CacheControl.noCache());
    }
}
