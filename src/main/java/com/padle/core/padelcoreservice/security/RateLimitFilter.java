package com.padle.core.padelcoreservice.security;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Rate limiting filter на основе IP адреса.
 *
 * Лимиты (bucket4j, окно сброса — 1 минута):
 *   AUTH    /login, /api/auth/**, /oauth2/**, /login/oauth2/**  — 10 запросов / минуту
 *   REGISTER /players/registro, /recuperar-password,
 *            /double-registration/**                            — 3 запроса / минуту
 *   API     /api/** (остальные)                                 — 60 запросов / минуту
 *   GENERAL все остальные endpoints                             — 200 запросов / минуту
 *
 * Блокировка подозрительных IP:
 *   — каждый запрос на REGISTER увеличивает счётчик на +1
 *   — провал регистрации (из контроллера) увеличивает счётчик на +2
 *   — при счётчике >= 5 за 15 минут IP получает статус BLOCKED (1 запрос / 15 мин)
 *   — счётчик сбрасывается автоматически через 15 минут
 *
 * Статические ресурсы (/css/, /js/, /images/, /webjars/, favicon, robots.txt)
 * не учитываются в лимитах.
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // IP-адреса, которые полностью обходят rate limiting (разработчик / владелец / мониторинг).
    // Тот же список, что в nginx geo $ratelimit_key и CrowdSec whitelist (см. SECURITY.md) —
    // вынесен в конфигурацию, чтобы не держать его как отдельную константу, которую легко
    // забыть синхронизировать при добавлении нового доверенного IP.
    @Value("${app.security.whitelisted-ips:194.124.210.113,152.171.139.176,167.235.34.217}")
    private String whitelistedIpsProperty;

    private Set<String> whitelistedIps;

    @PostConstruct
    private void initWhitelistedIps() {
        whitelistedIps = Arrays.stream(whitelistedIpsProperty.split(","))
                .map(String::trim)
                .filter(ip -> !ip.isEmpty())
                .collect(Collectors.toSet());
    }

    // Кэш buckets по ключу "IP:endpoint_type". expireAfterAccess вместо неограниченного
    // ConcurrentHashMap — иначе при атаке с большим числом уникальных IP карта растёт
    // без ограничений (DoS-вектор против памяти самого приложения). TTL взят с запасом
    // от самого длинного окна лимита (1 минута для AUTH/REGISTER/API/GENERAL) — для активных
    // IP бакет не сбрасывается (expireAfterAccess продлевает жизнь при каждом обращении),
    // для простаивающих 15+ минут — пересоздаётся с нуля, что эквивалентно тому, что и так
    // происходило бы естественным пополнением bucket4j-окна.
    private final Cache<String, Bucket> buckets = CacheBuilder.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(40_000)
            .build();

    // ✅ Кэш для отслеживания ПОДОЗРИТЕЛЬНЫХ регистраций с IP
    // Если с IP пришло 3+ регистрации за 15 минут → блокируем
    private final Cache<String, Integer> suspiciousRegistrations = CacheBuilder.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    // Счётчик заблокированных запросов за текущие сутки (сбрасывается в 23:55 планировщиком)
    public static final AtomicInteger BLOCKED_TODAY = new AtomicInteger(0);

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

        if (whitelistedIps.contains(ip)) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        LimitType limitType = classifyPath(path, ip);  // ← передаём ip

        if (limitType == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (limitType == LimitType.BLOCKED) {
            BLOCKED_TODAY.incrementAndGet();
            sendRateLimitResponse(request, response, LimitType.BLOCKED);
            return;
        }

        String bucketKey = ip + ":" + limitType.name();
        Bucket bucket = getOrCreateBucket(bucketKey, limitType);

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded: ip={}, path={}, type={}", ip, path, limitType);
            BLOCKED_TODAY.incrementAndGet();
            sendRateLimitResponse(request, response, limitType);
        }
    }

    // ✅ Метод с параметром ip
    private LimitType classifyPath(String path, String ip) {
        // Статические ресурсы и служебные страницы ошибок — пропускаем без лимита
        if (path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/images/") || path.startsWith("/webjars/")
                || path.equals("/favicon.ico") || path.equals("/robots.txt")
                || path.startsWith("/error/")) {
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
        if (path.startsWith("/players/registro") || path.startsWith("/players/api/registro")
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

    private Bucket getOrCreateBucket(String bucketKey, LimitType type) {
        try {
            return buckets.get(bucketKey, () -> createBucket(type));
        } catch (ExecutionException e) {
            // createBucket() не бросает checked-исключений — сюда попасть невозможно
            throw new IllegalStateException("Failed to create rate limit bucket for key: " + bucketKey, e);
        }
    }



    private String extractIp(HttpServletRequest request) {
        // X-Real-IP is set by Nginx to $remote_addr (real IP after Cloudflare real_ip_module).
        // Trusting CF-Connecting-IP directly would allow forgery on non-Cloudflare connections.
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void sendRateLimitResponse(HttpServletRequest request,
                                       HttpServletResponse response,
                                       LimitType type) throws IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        int retryAfter = (type == LimitType.BLOCKED) ? 900 : 60;

        // BLOCKED на браузерных путях → отдельная страница с объяснением
        if (type == LimitType.BLOCKED && !path.startsWith("/api/")) {
            response.sendRedirect("/error/rate-limit?retry=900&blocked=true");
            return;
        }

        // Form POST /login → редирект на страницу логина с ошибкой
        if ("POST".equalsIgnoreCase(method) && path.equals("/login")) {
            response.sendRedirect("/login?error=rate-limit");
            return;
        }

        // Form POST /players/registro → редирект на форму регистрации с ошибкой
        if ("POST".equalsIgnoreCase(method) && path.equals("/players/registro")) {
            response.sendRedirect("/players/registro?error=rate-limit");
            return;
        }

        // GENERAL на браузерных путях → стилизованная страница 429
        if (type == LimitType.GENERAL) {
            response.sendRedirect("/error/rate-limit?retry=60");
            return;
        }

        // Всё остальное (API, OAuth2, recuperar-password и т.д.) → JSON
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", String.valueOf(retryAfter));

        String message = switch (type) {
            case AUTH -> "Demasiados intentos. Intenta en 1 minuto.";
            case REGISTER -> "Demasiadas solicitudes. Intenta en 1 minuto.";
            case BLOCKED -> "IP bloqueado por actividad sospechosa. Intenta en 15 minutos.";
            default -> "Demasiadas solicitudes. Intenta en 1 minuto.";
        };
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + message + "\",\"retryAfter\":" + retryAfter + "}");
    }
}