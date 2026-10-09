package com.padle.core.padelcoreservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * LFPT-0518: короткий сквозной идентификатор на один HTTP-запрос — кладётся в MDC,
 * чтобы его можно было сослаться в структурированных Telegram-алертах (logging/StructuredAlert)
 * и в Loki JSON-логах (плейсхолдер %X{requestId:-} в logback-spring.xml, до этого всегда пустой).
 * Регистрируется в SecurityConfig раньше RateLimitFilter, чтобы id был доступен уже на этапе
 * проверки rate limit.
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        MDC.put(MDC_KEY, UUID.randomUUID().toString().substring(0, 8));
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Поток Tomcat переиспользуется для следующего запроса — без явной очистки
            // requestId "утечёт" в логи, не относящиеся к этому запросу.
            MDC.remove(MDC_KEY);
        }
    }
}
