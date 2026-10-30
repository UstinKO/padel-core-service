package com.padle.core.padelcoreservice.controller.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.padle.core.padelcoreservice.marketing.MarketingContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/cookies")
@RequiredArgsConstructor
public class CookieController {

    private static final Duration CONSENT_MAX_AGE = Duration.ofDays(365);
    private final MarketingContextService marketing;

    @PostMapping("/accept")
    public ResponseEntity<?> acceptCookies(HttpServletRequest request, HttpServletResponse response) {
        log.info("Usuario aceptó todas las cookies");
        addConsentCookie(request, response, "cookieConsent", "accepted");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reject")
    public ResponseEntity<?> rejectCookies(HttpServletRequest request, HttpServletResponse response) {
        log.info("Usuario rechazó todas las cookies");
        addConsentCookie(request, response, "cookieConsent", "rejected");
        marketing.revoke(request, response);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/customize")
    public ResponseEntity<?> customizeCookies(@RequestParam(required = false) boolean analytics,
                                              @RequestParam(required = false) boolean marketing,
                                              HttpServletRequest request,
                                              HttpServletResponse response) {
        log.info("Usuario personalizó cookies - analytics: {}, marketing: {}", analytics, marketing);

        addConsentCookie(request, response, "cookieConsent", "customized");
        addConsentCookie(request, response, "analyticsConsent", String.valueOf(analytics));
        addConsentCookie(request, response, "marketingConsent", String.valueOf(marketing));
        if (!marketing) {
            this.marketing.revoke(request, response);
        }

        return ResponseEntity.ok().build();
    }

    // Secure определяется per-request через request.isSecure() (учитывает X-Forwarded-Proto
    // от Nginx благодаря forward-headers-strategy: framework в application.yml) — server.ssl.enabled
    // никогда не выставляется в этом деплое (TLS терминируется на Nginx), поэтому статичный флаг
    // не годится: и не отражал бы прод, и ломал бы cookie на http://localhost в dev.
    // jakarta.servlet.http.Cookie не поддерживает SameSite нативно — используем Spring
    // ResponseCookie.
    private void addConsentCookie(HttpServletRequest request, HttpServletResponse response,
                                   String name, String value) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .path("/")
                .maxAge(CONSENT_MAX_AGE)
                .httpOnly(false)
                .secure(request.isSecure())
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
