package com.padle.core.padelcoreservice.security;

import com.padle.core.application.identity.PlayerProfiles;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class GuestSessionFilter extends OncePerRequestFilter {
    private final PlayerProfiles players;
    private final SecurityContextLogoutHandler logout = new SecurityContextLogoutHandler();

    public GuestSessionFilter(PlayerProfiles players) {
        this.players = players;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PlayerPrincipal player
                && player.getIdentity().isGuestAccount()
                && !players.existsActiveByIdentity(player.getIdentity())) {
            logout.logout(request, response, auth);
        }
        chain.doFilter(request, response);
    }
}
