package com.padle.core.padelcoreservice.security;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.padle.core.padelcoreservice.logging.AlertSeverity;
import com.padle.core.padelcoreservice.logging.StructuredAlert;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import io.github.bucket4j.TimeMeter;
import com.google.common.base.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;
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

/** Ограничивает IP по назначению запроса; эскалация применяется только к признакам абьюза. */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final AccountResponses responses;
    private final LongSupplier millis;
    private final TimeMeter timeMeter;
    private final Cache<String, Long> blockedUntil;

    @Autowired
    public RateLimitFilter(AccountResponses responses) {
        this(responses, System::currentTimeMillis, TimeMeter.SYSTEM_MILLISECONDS, Ticker.systemTicker());
    }

    RateLimitFilter(AccountResponses responses, LongSupplier millis, TimeMeter timeMeter, Ticker ticker) {
        this.responses = responses;
        this.millis = millis;
        this.timeMeter = timeMeter;
        this.blockedUntil = CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumSize(10000).ticker(ticker).build();
    }

    @Value("${app.security.whitelisted-ips:}")
    private String whitelistedIpsProperty;

    private Set<String> whitelistedIps;

    @PostConstruct
    private void initWhitelistedIps() {
        whitelistedIps = Arrays.stream(whitelistedIpsProperty.split(","))
                .map(String::trim)
                .filter(ip -> !ip.isEmpty())
                .collect(Collectors.toSet());
    }

    private final Cache<String, Bucket> buckets = CacheBuilder.newBuilder()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .maximumSize(40_000)
            .build();

    private final Cache<String, Integer> suspiciousRegistrations = CacheBuilder.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

    private final Cache<String, Integer> suspiciousAuth = CacheBuilder.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(10000)
            .build();

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
        LimitType limitType = classifyPath(path, request.getMethod(), ip);

        if (limitType == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (limitType == LimitType.BLOCKED) {
            BLOCKED_TODAY.incrementAndGet();
            sendRateLimitResponse(request, response, LimitType.BLOCKED, blockedSeconds(ip));
            return;
        }

        String bucketKey = ip + ":" + limitType.name();
        Bucket bucket = getOrCreateBucket(bucketKey, limitType);

        var probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
        } else {
            StructuredAlert.of(AlertSeverity.SECURITY_WARNING, "Превышен лимит запросов", "RATE_LIMIT_EXCEEDED")
                    .field("IP", ip)
                    .field("Endpoint", request.getMethod() + " " + path)
                    .field("Limit type", limitType)
                    .warn(log);
            BLOCKED_TODAY.incrementAndGet();
            if (limitType == LimitType.AUTH) {
                incrementSuspiciousAuthCounter(ip);
            }
            if (limitType == LimitType.REGISTER) incrementSuspiciousCounter(ip);
            long retry = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
            if (isBlocked(ip)) {
                limitType = LimitType.BLOCKED;
                retry = blockedSeconds(ip);
            }
            sendRateLimitResponse(request, response, limitType, retry);
        }
    }

    private long blockedSeconds(String ip) {
        long now = millis.getAsLong();
        long until = Optional.ofNullable(blockedUntil.getIfPresent(ip)).orElse(now);
        return Math.max(1, (until - now + 999) / 1000);
    }

    private LimitType classifyPath(String path, String method, String ip) {
        if (path.startsWith("/css/") || path.startsWith("/js/")
                || path.startsWith("/images/") || path.startsWith("/webjars/")
                || path.startsWith("/web/")
                || path.equals("/favicon.ico") || path.equals("/robots.txt")
                || path.startsWith("/error/")) {
            return null;
        }

        if (isBlocked(ip)) {
            log.debug("IP временно заблокирован: {}", ip);
            return LimitType.BLOCKED;
        }

        boolean mutating = !Set.of("GET", "HEAD", "OPTIONS").contains(method);
        if ((mutating && (path.equals("/login") || path.startsWith("/api/auth/") || path.equals("/api/players/me/password")))
                || path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/")) {
            return LimitType.AUTH;
        }
        if (mutating && (path.startsWith("/players/registro") || path.startsWith("/players/api/registro")
                || path.equals("/api/players/registro") || path.startsWith("/api/password-reset/")
                || path.startsWith("/recuperar-password") || path.startsWith("/double-registration/"))) {
            return LimitType.REGISTER;
        }

        if (path.startsWith("/api/")) {
            return LimitType.API;
        }

        return LimitType.GENERAL;
    }

    private boolean isBlocked(String ip) {
        Long until = blockedUntil.getIfPresent(ip);
        return until != null && until > millis.getAsLong();
    }

    private void incrementSuspiciousCounter(String ip) {
        int newCount = suspiciousRegistrations.asMap().merge(ip, 1, Integer::sum);
        if (newCount == 5) {
            blockedUntil.put(ip, millis.getAsLong() + Duration.ofMinutes(15).toMillis());
            StructuredAlert.of(AlertSeverity.SECURITY_WARNING, "IP заблокирован за подозрительную активность", "SUSPICIOUS_ACTIVITY_BLOCKED")
                    .field("IP", ip)
                    .field("Reason", "REGISTER")
                    .field("Attempts", newCount)
                    .warn(log);
        }
    }

    private void incrementSuspiciousAuthCounter(String ip) {
        int newCount = suspiciousAuth.asMap().merge(ip, 1, Integer::sum);
        if (newCount == 5) {
            blockedUntil.put(ip, millis.getAsLong() + Duration.ofMinutes(15).toMillis());
            StructuredAlert.of(AlertSeverity.SECURITY_WARNING, "IP заблокирован за подозрительную активность", "SUSPICIOUS_ACTIVITY_BLOCKED")
                    .field("IP", ip)
                    .field("Reason", "AUTH")
                    .field("Attempts", newCount)
                    .warn(log);
        }
    }

    public void markRegistrationFailed(String ip) {
        int total = suspiciousRegistrations.asMap().merge(ip, 2, Integer::sum);
        if (total >= 5 && !isBlocked(ip)) blockedUntil.put(ip, millis.getAsLong() + Duration.ofMinutes(15).toMillis());
        StructuredAlert.of(AlertSeverity.SECURITY_WARNING, "IP заблокирован за подозрительную активность", "SUSPICIOUS_ACTIVITY_BLOCKED")
                .field("IP", ip)
                .field("Reason", "FAILED_REGISTRATION")
                .field("Attempts", total)
                .warn(log);
    }



    private Bucket createBucket(LimitType type) {
        Bandwidth limit = switch (type) {
            case AUTH -> Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)));
            case REGISTER -> Bandwidth.classic(3, Refill.intervally(3, Duration.ofMinutes(1)));
            case BLOCKED -> Bandwidth.classic(1, Refill.intervally(1, Duration.ofMinutes(15)));
            case API -> Bandwidth.classic(60, Refill.greedy(60, Duration.ofMinutes(1)));
            case GENERAL -> Bandwidth.classic(200, Refill.greedy(200, Duration.ofMinutes(1)));
        };
        return Bucket.builder().withCustomTimePrecision(timeMeter).addLimit(limit).build();
    }

    private Bucket getOrCreateBucket(String bucketKey, LimitType type) {
        try {
            return buckets.get(bucketKey, () -> createBucket(type));
        } catch (ExecutionException e) {
            throw new IllegalStateException("Не удалось создать ограничитель запросов: " + bucketKey, e);
        }
    }



    public String extractIp(HttpServletRequest request) {
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

    private void sendRateLimitResponse(HttpServletRequest request, HttpServletResponse response,
                                       LimitType type, long retryAfter) throws IOException {
        String path = request.getRequestURI();
        boolean json = path.startsWith("/api/") || AccountResponses.wantsJson(request);
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        if (!json) {
            if (type == LimitType.BLOCKED) {
                response.sendRedirect("/error/rate-limit?retry=" + retryAfter + "&blocked=true");
                return;
            }
            if ("POST".equals(request.getMethod()) && path.equals("/login")) {
                response.sendRedirect(TournamentLogin.failureUrl("rate-limit", TournamentLogin.requestedTournament(request)));
                return;
            }
            if ("POST".equals(request.getMethod()) && path.equals("/players/registro")) {
                response.sendRedirect("/players/registro?error=rate-limit");
                return;
            }
            if (type == LimitType.GENERAL) {
                response.sendRedirect("/error/rate-limit?retry=" + retryAfter);
                return;
            }
        }
        var problem = responses.problem(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", List.of(), request);
        problem.setProperty("retryAfter", retryAfter);
        problem.setProperty("blockedUntil", Instant.ofEpochMilli(millis.getAsLong() + retryAfter * 1000).toString());
        responses.json(response, 429, problem);
    }
}
