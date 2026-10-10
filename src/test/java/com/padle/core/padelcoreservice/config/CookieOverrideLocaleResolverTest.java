package com.padle.core.padelcoreservice.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

// LFPT-0541: ручной override языка (cookie "lang") с приоритетом над Accept-Language,
// откат на Accept-Language при отсутствии/невалидности cookie.
class CookieOverrideLocaleResolverTest {

    private static final List<Locale> SUPPORTED_LOCALES = List.of(
            Locale.forLanguageTag("es"),
            Locale.forLanguageTag("ru"),
            Locale.forLanguageTag("en")
    );

    private final CookieOverrideLocaleResolver resolver =
            new CookieOverrideLocaleResolver(SUPPORTED_LOCALES, Locale.forLanguageTag("es"));

    @Test
    void resolveLocale_sinCookieYSinAcceptLanguage_devuelveDefaultEs() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.forLanguageTag("es"));
    }

    @Test
    void resolveLocale_sinCookie_usaAcceptLanguageComoAntes() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "ru");

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.forLanguageTag("ru"));
    }

    @Test
    void resolveLocale_conCookieValida_tienePrioridadSobreAcceptLanguage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "ru");
        request.setCookies(new Cookie("lang", "en"));

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.forLanguageTag("en"));
    }

    @Test
    void resolveLocale_conCookieInvalida_vuelveAAcceptLanguage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "ru");
        request.setCookies(new Cookie("lang", "fr"));

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.forLanguageTag("ru"));
    }

    @Test
    void setLocale_conLocaleSoportado_escribeCookieConSecureSegunRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        resolver.setLocale(request, response, Locale.forLanguageTag("ru"));

        String setCookie = response.getHeader("Set-Cookie");
        assertThat(setCookie)
                .contains("lang=ru")
                .contains("Secure")
                .contains("SameSite=Lax");
    }

    @Test
    void setLocale_sobreHttpSinTls_noMarcaLaCookieComoSecure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        resolver.setLocale(request, response, Locale.forLanguageTag("ru"));

        assertThat(response.getHeader("Set-Cookie")).doesNotContain("Secure");
    }

    @Test
    void setLocale_conLocaleNoSoportado_noEscribeCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        resolver.setLocale(request, response, Locale.forLanguageTag("fr"));

        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void setLocale_luegoResolveLocaleEnElMismoRequest_devuelveElNuevoIdiomaDeInmediato() {
        // Simula lo que hace LocaleChangeInterceptor: setLocale() en preHandle, resolveLocale()
        // de nuevo al renderizar la vista — en el mismo request, sin que el cliente haya
        // reenviado la cookie todavía (eso solo pasaría en el siguiente request).
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en");
        MockHttpServletResponse response = new MockHttpServletResponse();

        resolver.setLocale(request, response, Locale.forLanguageTag("ru"));

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.forLanguageTag("ru"));
    }

    @Test
    void setLocale_conLocaleNull_noEscribeCookieNiLanzaExcepcion() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        resolver.setLocale(request, response, null);

        assertThat(response.getHeader("Set-Cookie")).isNull();
    }
}
