package com.padle.core.padelcoreservice.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Issue #278: до этого хендлера любой 403 (как из authorizeHttpRequests, так и из
 * @PreAuthorize) долетал до дефолтного Spring Boot Whitelabel Error Page — пользователь
 * видел голый трейс вместо понятного сообщения.
 *
 * Отдельный случай — GET /admin/tournaments/{id} у авторизованного не-админа: по логам
 * прод-инцидента (см. issue) это почти всегда значит, что человеку прислали админскую
 * ссылку вместо публичной. Вместо страницы с ошибкой сразу редиректим на публичную
 * страницу турнира (/torneo/{id} — универсальный роут, рендерит любой тип турнира).
 */
@Slf4j
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private static final Pattern ADMIN_TOURNAMENT_DETAILS = Pattern.compile("^/admin/tournaments/(\\d+)/?$");

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        String path = request.getRequestURI();
        log.warn("Access denied: {} {} ({})", request.getMethod(), path, accessDeniedException.getMessage());

        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"message\":\"Acceso denegado\"}");
            return;
        }

        if ("GET".equalsIgnoreCase(request.getMethod()) && isAuthenticated()) {
            Matcher matcher = ADMIN_TOURNAMENT_DETAILS.matcher(path);
            if (matcher.matches()) {
                response.sendRedirect("/torneo/" + matcher.group(1));
                return;
            }
        }

        response.sendRedirect("/error/403");
    }

    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());
    }
}
