package com.padle.core.padelcoreservice.config;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.SmartView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration(proxyBeanMethods = false)
public class AnalyticsTrafficConfig implements WebMvcConfigurer, HandlerInterceptor {
    private static final Set<String> STAFF_ROLES = Arrays.stream(OwnerRole.values())
            .map(role -> "ROLE_" + role.name()).collect(Collectors.toUnmodifiableSet());
    private final boolean production;

    public AnalyticsTrafficConfig(Environment environment) {
        production = environment.acceptsProfiles(Profiles.of("prod"));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView view) {
        if (view == null || view.wasCleared()
                || view.getView() instanceof SmartView smartView && smartView.isRedirectView()
                || view.getViewName() != null
                && (view.getViewName().startsWith("redirect:") || view.getViewName().startsWith("forward:"))) {
            return;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var path = request.getRequestURI().substring(request.getContextPath().length());
        var context = classify(authentication, path, testEvent(view));
        view.addObject("analyticsTraffic", context);
    }

    private static boolean testEvent(ModelAndView view) {
        Object tournament = view.getModel().get("tournament");
        return Boolean.TRUE.equals(view.getModel().get("analyticsTestTournament"))
                || tournament instanceof TournamentDto dto && Boolean.TRUE.equals(dto.getTestTournament())
                || tournament instanceof Tournament entity && Boolean.TRUE.equals(entity.getTestTournament());
    }

    TrafficContext classify(Authentication authentication, String path, boolean testEvent) {
        if (!production || path.equals("/preview") || path.startsWith("/preview/")
                || path.equals("/test") || path.startsWith("/test/")) {
            return new TrafficContext("test", "test_environment");
        }
        if (testEvent) {
            return new TrafficContext("test", "test_event");
        }
        if (path.equals("/admin") || path.startsWith("/admin/") || path.contains("/admin/")) {
            return new TrafficContext("internal", "admin_page");
        }
        boolean staff = Optional.ofNullable(authentication).filter(Authentication::isAuthenticated)
                .stream().flatMap(auth -> auth.getAuthorities().stream())
                .map(GrantedAuthority::getAuthority).anyMatch(STAFF_ROLES::contains);
        return staff ? new TrafficContext("internal", "staff_account")
                : new TrafficContext("visitor", "public");
    }

    public record TrafficContext(String group, String reason) { }
}
