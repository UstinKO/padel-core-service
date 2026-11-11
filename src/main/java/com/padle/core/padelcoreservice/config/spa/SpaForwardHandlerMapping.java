package com.padle.core.padelcoreservice.config.spa;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

public final class SpaForwardHandlerMapping extends AbstractHandlerMapping {

    private static final String SPA_INDEX = "/web/index.html";
    private static final PathPatternParser PARSER = PathPatternParser.defaultInstance;

    private final List<PathPattern> routes;
    private final HttpRequestHandler forwardHandler;

    public SpaForwardHandlerMapping(List<String> productionRoutes) {
        this(productionRoutes, (request, response) -> request.getRequestDispatcher(SPA_INDEX).forward(request, response));
    }

    public SpaForwardHandlerMapping(List<String> productionRoutes, HttpRequestHandler handler) {
        forwardHandler = handler;
        routes = productionRoutes.stream()
                .map(PARSER::parse)
                .toList();
    }

    @Override
    protected Object getHandlerInternal(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod()) && !HttpMethod.HEAD.matches(request.getMethod())) {
            return null;
        }

        var path = ServletRequestPathUtils.getParsedRequestPath(request).pathWithinApplication();
        return routes.stream().anyMatch(route -> route.matches(path)) ? forwardHandler : null;
    }
}
