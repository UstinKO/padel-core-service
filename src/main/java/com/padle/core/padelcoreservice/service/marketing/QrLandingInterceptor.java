package com.padle.core.padelcoreservice.service.marketing;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class QrLandingInterceptor implements HandlerInterceptor {
    private static final String SOURCE = QrLandingInterceptor.class.getName() + ".source";
    private final QrStatsService stats;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() == DispatcherType.REQUEST && "/".equals(request.getRequestURI())) {
            QrLandingPolicy.source(request.getParameterMap()).ifPresent(source -> {
                response.setHeader("Cache-Control", "no-store");
                if (QrRequestPolicy.shouldCount(request.getMethod(), request.getHeader("User-Agent"),
                        request.getHeader("Purpose"), request.getHeader("Sec-Purpose"))) {
                    request.setAttribute(SOURCE, source);
                }
            });
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object source = request.getAttribute(SOURCE);
        request.removeAttribute(SOURCE);
        if (ex == null && response.getStatus() == 200 && source instanceof String value) {
            stats.recordLanding(value);
        }
    }
}
