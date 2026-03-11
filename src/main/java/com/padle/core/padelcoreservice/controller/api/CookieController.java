package com.padle.core.padelcoreservice.controller.api;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/cookies")
public class CookieController {

    @PostMapping("/accept")
    public ResponseEntity<?> acceptCookies(HttpServletResponse response) {
        log.info("Usuario aceptó todas las cookies");

        // Устанавливаем cookie на 1 год
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("cookieConsent", "accepted");
        cookie.setMaxAge(365 * 24 * 60 * 60); // 1 año
        cookie.setPath("/");
        cookie.setHttpOnly(false);
        cookie.setSecure(false); // В продакшене должно быть true при HTTPS
        response.addCookie(cookie);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/reject")
    public ResponseEntity<?> rejectCookies(HttpServletResponse response) {
        log.info("Usuario rechazó todas las cookies");

        // Устанавливаем cookie на 1 год
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("cookieConsent", "rejected");
        cookie.setMaxAge(365 * 24 * 60 * 60); // 1 año
        cookie.setPath("/");
        cookie.setHttpOnly(false);
        cookie.setSecure(false); // В продакшене должно быть true при HTTPS
        response.addCookie(cookie);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/customize")
    public ResponseEntity<?> customizeCookies(@RequestParam(required = false) boolean analytics,
                                              @RequestParam(required = false) boolean marketing,
                                              HttpServletResponse response) {
        log.info("Usuario personalizó cookies - analytics: {}, marketing: {}", analytics, marketing);

        // Сохраняем настройки в cookie
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("cookieConsent", "customized");
        cookie.setMaxAge(365 * 24 * 60 * 60);
        cookie.setPath("/");
        cookie.setHttpOnly(false);
        response.addCookie(cookie);

        jakarta.servlet.http.Cookie analyticsCookie = new jakarta.servlet.http.Cookie("analyticsConsent", String.valueOf(analytics));
        analyticsCookie.setMaxAge(365 * 24 * 60 * 60);
        analyticsCookie.setPath("/");
        analyticsCookie.setHttpOnly(false);
        response.addCookie(analyticsCookie);

        jakarta.servlet.http.Cookie marketingCookie = new jakarta.servlet.http.Cookie("marketingConsent", String.valueOf(marketing));
        marketingCookie.setMaxAge(365 * 24 * 60 * 60);
        marketingCookie.setPath("/");
        marketingCookie.setHttpOnly(false);
        response.addCookie(marketingCookie);

        return ResponseEntity.ok().build();
    }
}