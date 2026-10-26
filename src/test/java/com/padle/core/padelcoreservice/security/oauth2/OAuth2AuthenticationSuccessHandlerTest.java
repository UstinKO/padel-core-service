package com.padle.core.padelcoreservice.security.oauth2;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.security.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
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
 * Google OAuth2-логин должен выставлять remember-me cookie, хотя у кнопки
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
                new OAuth2AuthenticationSuccessHandler(rememberMeServices, mock(JwtService.class));

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

    /**
     * Вход, инициированный клиентом 1Padel.App ({@code ?client=app} на
     * {@code /oauth2/authorization/google}, перехваченный {@link OAuth2AppLoginOriginFilter}),
     * должен вернуть браузер в приложение с токенами во fragment URL, а не на /players/dashboard.
     */
    @Test
    void onAuthenticationSuccess_loginDesdeApp_redirigeAAppConTokensEnFragment() throws Exception {
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

        JwtService jwtService = mock(JwtService.class);
        when(jwtService.generateToken(player, player.getId(), player.getNombreCompleto())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(player, player.getId())).thenReturn("refresh-token");
        when(jwtService.getAccessTokenExpirationMs()).thenReturn(86_400_000L);

        OAuth2AuthenticationSuccessHandler handler =
                new OAuth2AuthenticationSuccessHandler(rememberMeServices, jwtService);

        CustomOAuth2User principal = new CustomOAuth2User(mock(org.springframework.security.oauth2.core.user.OAuth2User.class), player);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authentication.getName()).thenReturn(PLAYER_EMAIL);

        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute(OAuth2AppLoginOriginFilter.SESSION_ATTR)).thenReturn(Boolean.TRUE);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter(any())).thenReturn(null);
        when(request.getContextPath()).thenReturn("");
        when(request.getSession(false)).thenReturn(session);

        HttpServletResponse response = mock(HttpServletResponse.class);
        // DefaultRedirectStrategy codifica la URL vía encodeRedirectURL() antes de sendRedirect() —
        // sin este stub, el mock devuelve null y se pierde la URL real en la verificación.
        when(response.encodeRedirectURL(any())).thenAnswer(invocation -> invocation.getArgument(0));

        handler.onAuthenticationSuccess(request, response, authentication);

        org.mockito.Mockito.verify(session).removeAttribute(OAuth2AppLoginOriginFilter.SESSION_ATTR);
        org.mockito.Mockito.verify(response).sendRedirect(
                "/app/#accessToken=access-token&refreshToken=refresh-token&expiresIn=86400000");
    }
}
