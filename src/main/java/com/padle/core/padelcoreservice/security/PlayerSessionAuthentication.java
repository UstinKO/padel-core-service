package com.padle.core.padelcoreservice.security;

import com.padle.core.padelcoreservice.security.oauth2.OAuth2PlayerPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class PlayerSessionAuthentication {

    private final HttpSessionSecurityContextRepository sessions = new HttpSessionSecurityContextRepository();

    public PlayerSessionAuthentication() {
        sessions.setAllowSessionCreation(false);
    }

    public void replace(Authentication current, PlayerPrincipal principal,
                        HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session == null || !(session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) instanceof SecurityContext stored)
                || stored.getAuthentication() != current) {
            return;
        }

        PlayerPrincipal replacement = current.getPrincipal() instanceof OAuth2PlayerPrincipal oauth
                ? oauth.withPlayer(principal)
                : principal;
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(current.toBuilder().principal(replacement).build());
        SecurityContextHolder.setContext(context);
        sessions.saveContext(context, request, response);
    }
}
