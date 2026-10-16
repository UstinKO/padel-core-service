package com.padle.core.padelcoreservice.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// LFPT-0608: только access-токен должен аутентифицировать запрос — refresh-токен
// (tokenType=refresh) подписан тем же секретом и раньше проходил validateToken() так же,
// как access, что позволяло использовать его как access-токен все 7 дней его жизни.
class JwtAuthenticationFilterTest {

    private static final String EMAIL = "player@example.com";

    private final JwtService jwtService = mock(JwtService.class);
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);

    // SecurityContextHolder — глобальный статический ThreadLocal: другие тестовые классы
    // (например, OAuth2AuthenticationSuccessHandlerTest, вызывающий реальный хендлер) могут
    // оставить в нём установленный Authentication, если Surefire выполняет тесты в одном
    // потоке (порядок между окружениями не детерминирован — упало именно так в CI, LFPT-0608).
    // Очищаем и до теста, не только после.
    @BeforeEach
    @AfterEach
    void limpiarSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_conAccessTokenValido_estableceAuthentication() throws Exception {
        UserDetails userDetails = accessUser();
        mockToken("access-token", userDetails, true, false);

        runFilter("access-token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(userDetails);
    }

    @Test
    void doFilterInternal_conRefreshTokenValido_noEstableceAuthentication() throws Exception {
        UserDetails userDetails = accessUser();
        mockToken("refresh-token", userDetails, true, true);

        runFilter("refresh-token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternal_conTokenInvalido_noEstableceAuthentication() throws Exception {
        UserDetails userDetails = accessUser();
        mockToken("expired-token", userDetails, false, false);

        runFilter("expired-token");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void mockToken(String token, UserDetails userDetails, boolean validateResult, boolean isRefresh) {
        when(jwtService.extractUsername(token)).thenReturn(EMAIL);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(userDetails);
        when(jwtService.validateToken(eq(token), any())).thenReturn(validateResult);
        when(jwtService.isRefreshToken(token)).thenReturn(isRefresh);
    }

    private void runFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);
    }

    private UserDetails accessUser() {
        return new User(EMAIL, "n/a", java.util.List.of());
    }
}
