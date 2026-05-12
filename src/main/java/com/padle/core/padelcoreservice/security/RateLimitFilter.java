package com.padle.core.padelcoreservice.security;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
import java.util.concurrent.TimeUnit;

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

    // ✅ Кэш для отслеживания ПОДОЗРИТЕЛЬНЫХ регистраций с IP
    // Если с IP пришло 3+ регистрации за 15 минут → блокируем
    private final Cache<String, Integer> suspiciousRegistrations = CacheBuilder.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    private enum LimitType {
        AUTH,        // логин, токены
        REGISTER,    // регистрация, восстановление пароля
        BLOCKED,     // IP временно заблокирован за подозрительную активность
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
        LimitType limitType = classifyPath(path, ip);  // ← передаём ip

        if (limitType == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (limitType == LimitType.BLOCKED) {
            sendRateLimitResponse(response, LimitType.BLOCKED);
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

    // ✅ Метод с параметром ip
    private LimitType classifyPath(String path, String ip) {
        // Статические ресурсы — пропускаем без лимита
        if (path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/images/") || path.startsWith("/webjars/")
                || path.equals("/favicon.ico") || path.equals("/robots.txt")) {
            return null;
        }

        // Проверяем — не заблокирован ли IP по подозрительной активности
        if (isBlocked(ip)) {
            log.warn("IP bloqueado por actividad sospechosa: {}", ip);
            return LimitType.BLOCKED;
        }

        // Аутентификация
        if (path.equals("/login") || path.startsWith("/api/auth/")
                || path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/")) {
            return LimitType.AUTH;
        }

        // Регистрация и восстановление пароля
        if (path.startsWith("/players/registro") || path.startsWith("/api/players/registro")
                || path.startsWith("/recuperar-password")
                || path.startsWith("/double-registration/")) {
            // Увеличиваем счётчик подозрительных регистраций
            incrementSuspiciousCounter(ip);
            return LimitType.REGISTER;
        }

        // API endpoints
        if (path.startsWith("/api/")) {
            return LimitType.API;
        }

        return LimitType.GENERAL;
    }

    // ✅ Проверка — заблокирован ли IP
    private boolean isBlocked(String ip) {
        Integer count = suspiciousRegistrations.getIfPresent(ip);
        return count != null && count >= 5;  // 5+ регистраций за 15 мин → блокировка
    }

    // ✅ Увеличиваем счётчик при КАЖДОМ запросе на регистрацию
    private void incrementSuspiciousCounter(String ip) {
        Integer count = suspiciousRegistrations.getIfPresent(ip);
        if (count == null) {
            suspiciousRegistrations.put(ip, 1);
        } else {
            suspiciousRegistrations.put(ip, count + 1);
        }
    }

    // ✅ Публичный метод для ручного увеличения счётчика (из контроллера)
    public void markRegistrationFailed(String ip) {
        Integer count = suspiciousRegistrations.getIfPresent(ip);
        suspiciousRegistrations.put(ip, count != null ? count + 2 : 2);  // +2 за фейл
        log.warn("Failed registration from IP: {}, total suspicious: {}",
                ip, suspiciousRegistrations.getIfPresent(ip));
    }



    private Bucket createBucket(LimitType type) {
        Bandwidth limit = switch (type) {
            case AUTH -> Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)));
            // ✅ Ужесточаем: 3 регистрации в минуту вместо 5
            case REGISTER -> Bandwidth.classic(3, Refill.intervally(3, Duration.ofMinutes(1)));
            case BLOCKED -> Bandwidth.classic(1, Refill.intervally(1, Duration.ofMinutes(15)));
            case API -> Bandwidth.classic(60, Refill.greedy(60, Duration.ofMinutes(1)));
            case GENERAL -> Bandwidth.classic(200, Refill.greedy(200, Duration.ofMinutes(1)));
        };
        return Bucket.builder().addLimit(limit).build();
    }



    private String extractIp(HttpServletRequest request) {
        String cfConnectingIp = request.getHeader("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isBlank()) {
            return cfConnectingIp;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
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
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", "60");

        String message = switch (type) {
            case AUTH -> "Demasiados intentos. Intenta en 1 minuto.";
            case REGISTER -> "Demasiadas solicitudes. Intenta en 1 minuto.";
            case BLOCKED -> "IP bloqueado por actividad sospechosa. Intenta en 15 minutos.";
            default -> "Demasiadas solicitudes. Intenta en 1 minuto.";
        };

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