package com.padle.core.padelcoreservice.security.oauth2;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Issue #297: Google OAuth2-логин должен выставлять remember-me cookie, хотя у кнопки
 * "Войти через Google" нет чекбокса "запомнить меня" и параметр remember-me неоткуда
 * взять из запроса. Проверяем именно этот механизм — реальный
 * {@link TokenBasedRememberMeServices} (не мок), чтобы убедиться, что подмена параметра
 * запроса в обработчике действительно заставляет Spring Security выставить cookie.
 */
class OAuth2AuthenticationSuccessHandlerTest {

    private static final String PLAYER_EMAIL = "oauth-player@example.com";
    private static final String PLAYER_PASSWORD_HASH = "$2a$10$fakeEncodedPasswordHashForTest";

    @Test
    void onAuthenticationSuccess_setsRememberMeCookie_forOAuth2Login() throws Exception {
        PlayerPadel player = PlayerPadel.builder()
                .id(1L)
                .nombre("Test")
                .apellido("Player")
                .email(PLAYER_EMAIL)
                .passwordHash(PLAYER_PASSWORD_HASH)
                .activo(true)
                .emailConfirmado(true)
                .build();

        UserDetailsService userDetailsService = username -> {
            if (PLAYER_EMAIL.equals(username)) {
                return player;
            }
            throw new UsernameNotFoundException(username);
        };

        RememberMeServices rememberMeServices =
                new TokenBasedRememberMeServices("test-key", userDetailsService);

        OAuth2AuthenticationSuccessHandler handler =
                new OAuth2AuthenticationSuccessHandler(rememberMeServices);

        CustomOAuth2User principal = new CustomOAuth2User(mock(org.springframework.security.oauth2.core.user.OAuth2User.class), player);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authentication.getName()).thenReturn(PLAYER_EMAIL);

        HttpServletRequest request = mock(HttpServletRequest.class);
        // Ни один параметр (в т.ч. "remember-me") реально не передан — как это и бывает
        // на колбэке /login/oauth2/code/google, который формирует сам Google.
        when(request.getParameter(any())).thenReturn(null);
        when(request.getContextPath()).thenReturn("");

        HttpServletResponse response = mock(HttpServletResponse.class);

        handler.onAuthenticationSuccess(request, response, authentication);

        org.mockito.ArgumentCaptor<Cookie> cookieCaptor = org.mockito.ArgumentCaptor.forClass(Cookie.class);
        org.mockito.Mockito.verify(response).addCookie(cookieCaptor.capture());

        Cookie rememberMeCookie = cookieCaptor.getValue();
        assertThat(rememberMeCookie.getName())
                .isEqualTo(AbstractRememberMeServices.SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY);
        assertThat(rememberMeCookie.getValue()).isNotBlank();
        // Кука должна пережить закрытие браузера — положительный maxAge (не session cookie).
        assertThat(rememberMeCookie.getMaxAge()).isGreaterThan(0);
    }
}
