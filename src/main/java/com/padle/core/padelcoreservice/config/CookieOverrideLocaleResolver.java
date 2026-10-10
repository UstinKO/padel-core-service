package com.padle.core.padelcoreservice.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Резолвер локали с ручным override: сначала смотрит cookie {@value #COOKIE_NAME}
 * (выставляется через {@code ?lang=} и {@link org.springframework.web.servlet.i18n.LocaleChangeInterceptor}),
 * и только если её нет или значение не входит в поддерживаемые локали — откатывается
 * на прежнее поведение ({@code Accept-Language}, см. {@link AcceptHeaderLocaleResolver}).
 */
public class CookieOverrideLocaleResolver implements LocaleResolver {

    static final String COOKIE_NAME = "lang";
    private static final Duration COOKIE_MAX_AGE = Duration.ofDays(365);
    // Request-атрибут — кэш резолва на один запрос. Без него setLocale() (вызывается
    // LocaleChangeInterceptor.preHandle ДО рендеринга) не повлиял бы на текущий ответ:
    // RequestContext/Thymeleaf зовут resolveLocale(request) заново при рендере, а Set-Cookie
    // из этого же ответа клиент ещё не прислал обратно — без кэша страница отрисовалась бы
    // на старой локали, и выбор применился бы только со следующего запроса (как у Spring
    // CookieLocaleResolver — тот же приём, LOCALE_REQUEST_ATTRIBUTE_NAME).
    private static final String LOCALE_REQUEST_ATTRIBUTE =
            CookieOverrideLocaleResolver.class.getName() + ".LOCALE";

    private final List<Locale> supportedLocales;
    private final AcceptHeaderLocaleResolver fallbackResolver;

    public CookieOverrideLocaleResolver(List<Locale> supportedLocales, Locale defaultLocale) {
        this.supportedLocales = supportedLocales;
        this.fallbackResolver = new AcceptHeaderLocaleResolver();
        this.fallbackResolver.setDefaultLocale(defaultLocale);
        this.fallbackResolver.setSupportedLocales(supportedLocales);
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        Object cached = request.getAttribute(LOCALE_REQUEST_ATTRIBUTE);
        if (cached instanceof Locale cachedLocale) {
            return cachedLocale;
        }
        Locale resolved = findCookieLocale(request)
                .orElseGet(() -> fallbackResolver.resolveLocale(request));
        request.setAttribute(LOCALE_REQUEST_ATTRIBUTE, resolved);
        return resolved;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        if (locale == null || !supportedLocales.contains(locale)) {
            // Невалидное значение ?lang= (вне {es, ru, en}) — резолвинг остаётся на Accept-Language,
            // в cookie ничего не пишем (не пропускаем непроверенное значение дальше).
            return;
        }
        writeCookie(request, response, locale);
        request.setAttribute(LOCALE_REQUEST_ATTRIBUTE, locale);
    }

    private Optional<Locale> findCookieLocale(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .map(Locale::forLanguageTag)
                .filter(supportedLocales::contains)
                .findFirst();
    }

    private void writeCookie(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // Secure определяется per-request через request.isSecure() (не статичный флаг) — TLS
        // терминируется на Nginx, см. тот же паттерн и обоснование в CookieController.addConsentCookie.
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, locale.getLanguage())
                .path("/")
                .maxAge(COOKIE_MAX_AGE)
                .httpOnly(false)
                .secure(request.isSecure())
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
