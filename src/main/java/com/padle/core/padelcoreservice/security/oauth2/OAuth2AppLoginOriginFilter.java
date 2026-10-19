package com.padle.core.padelcoreservice.security.oauth2;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * LFPT-0637: запоминает в сессии, что вход через Google инициирован клиентом 1Padel.App
 * (`?client=app` на самом же эндпоинте инициации, не произвольный redirect_uri — иначе
 * открылся бы open redirect), чтобы {@link OAuth2AuthenticationSuccessHandler} после
 * обмена кода с Google вернул браузер в приложение, а не на /players/dashboard.
 */
@Component
public class OAuth2AppLoginOriginFilter extends OncePerRequestFilter {

    public static final String SESSION_ATTR = "OAUTH2_APP_LOGIN";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if ("/oauth2/authorization/google".equals(request.getRequestURI())
                && "app".equals(request.getParameter("client"))) {
            request.getSession(true).setAttribute(SESSION_ATTR, Boolean.TRUE);
        }
        filterChain.doFilter(request, response);
    }
}
