package com.padle.core.padelcoreservice.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting filter на основе IP адреса.
 *
 * Лимиты:
 *   /login, /api/auth/**        — 10 запросов / минуту (защита от brute force)
 *   /players/registro/**        — 5 запросов / минуту (защита от регистрации ботов)
 *   /recuperar-password/**      — 5 запросов / минуту
 *   /api/** (остальные)         — 60 запросов / минуту
 *   Все остальные endpoints     — 200 запросов / минуту
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // Кэш buckets по ключу "IP:endpoint_type"
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // Типы лимитов
    private enum LimitType {
        AUTH,        // логин, токены
        REGISTER,    // регистрация, восстановление пароля
        API,         // остальные API запросы
        GENERAL      // публичные страницы
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String ip = extractIp(request);
        String path = request.getRequestURI();
        LimitType limitType = classifyPath(path);

        // Публичные статические ресурсы — не лимитируем
        if (limitType == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String bucketKey = ip + ":" + limitType.name();
        Bucket bucket = buckets.computeIfAbsent(bucketKey, k -> createBucket(limitType));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded: ip={}, path={}, type={}", ip, path, limitType);
            sendRateLimitResponse(response, limitType);
        }
    }

    private LimitType classifyPath(String path) {
        // Статические ресурсы — пропускаем без лимита
        if (path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/images/") || path.startsWith("/webjars/")
                || path.equals("/favicon.ico") || path.equals("/robots.txt")) {
            return null;
        }

        // Аутентификация — самый строгий лимит
        if (path.equals("/login") || path.startsWith("/api/auth/")
                || path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/")) {
            return LimitType.AUTH;
        }

        // Регистрация и восстановление пароля
        if (path.startsWith("/players/registro") || path.startsWith("/api/players/registro")
                || path.startsWith("/recuperar-password")
                || path.startsWith("/double-registration/")) {
            return LimitType.REGISTER;
        }

        // API endpoints
        if (path.startsWith("/api/")) {
            return LimitType.API;
        }

        // Всё остальное — публичные страницы
        return LimitType.GENERAL;
    }

    private Bucket createBucket(LimitType type) {
        Bandwidth limit = switch (type) {
            // 10 попыток логина в минуту с одного IP
            case AUTH -> Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)));
            // 5 регистраций в минуту
            case REGISTER -> Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
            // 60 API запросов в минуту
            case API -> Bandwidth.classic(60, Refill.greedy(60, Duration.ofMinutes(1)));
            // 200 запросов в минуту для публичных страниц
            case GENERAL -> Bandwidth.classic(200, Refill.greedy(200, Duration.ofMinutes(1)));
        };
        return Bucket.builder().addLimit(limit).build();
    }

    private String extractIp(HttpServletRequest request) {
        // Учитываем Cloudflare и другие proxy
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isBlank()) {
            return cfConnectingIp;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // Берём первый IP из цепочки
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    private void sendRateLimitResponse(HttpServletResponse response,
                                       LimitType type) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // 429
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", "60");

        String message = switch (type) {
            case AUTH -> "Demasiados intentos de inicio de sesión. Intenta de nuevo en 1 minuto.";
            case REGISTER -> "Demasiadas solicitudes de registro. Intenta de nuevo en 1 minuto.";
            default -> "Demasiadas solicitudes. Intenta de nuevo en 1 minuto.";
        };

        // Для обычных страниц — редирект вместо JSON
        if (type == LimitType.GENERAL) {
            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().write(
                    "<html><body><h2>429 - Demasiadas solicitudes</h2>" +
                            "<p>" + message + "</p>" +
                            "<a href='/'>Volver al inicio</a></body></html>"
            );
        } else {
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"" + message + "\",\"retryAfter\":60}"
            );
        }
    }
}