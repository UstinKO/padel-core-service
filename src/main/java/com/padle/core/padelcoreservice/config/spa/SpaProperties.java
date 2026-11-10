package com.padle.core.padelcoreservice.config.spa;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.web.util.pattern.PatternParseException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

@ConfigurationProperties("app.spa")
public record SpaProperties(@DefaultValue List<String> routes) {

    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;
    private static final List<String> RESERVED_PATHS = List.of(
            "/api", "/ws", "/actuator", "/oauth2", "/login/oauth2",
            "/logout", "/app", "/web", "/preview"
    );

    public SpaProperties {
        routes = normalize(Objects.requireNonNullElse(routes, List.of()));
    }

    private static List<String> normalize(List<String> routes) {
        var normalized = new LinkedHashSet<String>();
        routes.stream()
                .map(route -> Objects.requireNonNullElse(route, "").strip())
                .peek(SpaProperties::validate)
                .forEach(normalized::add);
        return List.copyOf(normalized);
    }

    private static void validate(String route) {
        if (route.isBlank()) {
            throw new IllegalArgumentException("app.spa.routes содержит пустой path pattern");
        }
        if (!route.startsWith("/")) {
            throw new IllegalArgumentException("app.spa.routes должен содержать абсолютные path patterns: " + route);
        }

        var pattern = parse(route);
        if (isCatchAll(pattern)) {
            throw new IllegalArgumentException("app.spa.routes запрещает глобальный catch-all: " + route);
        }
        if (overlapsReservedPath(pattern)) {
            throw new IllegalArgumentException("app.spa.routes пересекает зарезервированное пространство: " + route);
        }
    }

    private static PathPattern parse(String route) {
        try {
            return PARSER.parse(route);
        } catch (PatternParseException exception) {
            throw new IllegalArgumentException("Некорректный Spring path pattern в app.spa.routes: " + route, exception);
        }
    }

    private static boolean isCatchAll(PathPattern pattern) {
        return matches(pattern, "/") && matches(pattern, "/spa-route-validation/deep-link");
    }

    private static boolean overlapsReservedPath(PathPattern pattern) {
        return RESERVED_PATHS.stream()
                .anyMatch(path -> overlapsReservedPath(pattern, path));
    }

    private static boolean overlapsReservedPath(PathPattern pattern, String reservedPath) {
        var routeSegments = pathSegments(pattern.getPatternString());
        var reservedSegments = pathSegments(reservedPath);
        var comparedSegments = Math.min(routeSegments.size(), reservedSegments.size());
        var compatiblePrefix = IntStream.range(0, comparedSegments)
                .allMatch(index -> mayMatch(routeSegments.get(index), reservedSegments.get(index)));
        if (!compatiblePrefix) {
            return false;
        }
        if (routeSegments.size() >= reservedSegments.size()) {
            return true;
        }
        return isMultiSegmentWildcard(routeSegments.get(routeSegments.size() - 1));
    }

    private static List<String> pathSegments(String path) {
        return List.of(path.substring(1).split("/"));
    }

    private static boolean mayMatch(String routeSegment, String reservedSegment) {
        return routeSegment.equals(reservedSegment)
                || routeSegment.indexOf('{') >= 0
                || routeSegment.indexOf('*') >= 0
                || routeSegment.indexOf('?') >= 0;
    }

    private static boolean isMultiSegmentWildcard(String routeSegment) {
        return routeSegment.equals("**") || routeSegment.startsWith("{*");
    }

    private static boolean matches(PathPattern pattern, String path) {
        return pattern.matches(PathContainer.parsePath(path));
    }
}
