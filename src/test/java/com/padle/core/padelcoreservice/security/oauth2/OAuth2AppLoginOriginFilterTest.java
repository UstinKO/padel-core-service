package com.padle.core.padelcoreservice.security.oauth2;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LFPT-0637: el filtro solo debe marcar la sesión cuando el login de Google se
 * inicia explícitamente con {@code ?client=app} en el propio endpoint de inicio —
 * nunca a partir de un redirect_uri arbitrario (evita un open redirect).
 */
class OAuth2AppLoginOriginFilterTest {

    private final OAuth2AppLoginOriginFilter filter = new OAuth2AppLoginOriginFilter();

    @Test
    void marcaLaSesion_cuandoClientEsApp() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        HttpSession session = mock(HttpSession.class);

        when(request.getRequestURI()).thenReturn("/oauth2/authorization/google");
        when(request.getParameter("client")).thenReturn("app");
        when(request.getSession(true)).thenReturn(session);

        filter.doFilter(request, response, chain);

        verify(session).setAttribute(OAuth2AppLoginOriginFilter.SESSION_ATTR, Boolean.TRUE);
        verify(chain).doFilter(request, response);
    }

    @Test
    void noMarcaNada_siNoHayParametroClient() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/oauth2/authorization/google");
        when(request.getParameter("client")).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(request, never()).getSession(true);
        verify(chain).doFilter(request, response);
    }

    @Test
    void noMarcaNada_enOtraUrlAunqueTengaClientApp() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getRequestURI()).thenReturn("/oauth2/authorization/facebook");
        when(request.getParameter(anyString())).thenReturn("app");

        filter.doFilter(request, response, chain);

        verify(request, never()).getSession(true);
        verify(chain).doFilter(request, response);
    }
}
