package com.padle.core.padelcoreservice.security;

import com.padle.core.padelcoreservice.mapper.PlayerPrincipalMapper;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class LegacyPlayerSessionFilter extends OncePerRequestFilter {

    private final PlayerSessionAuthentication sessions;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PlayerPadel legacy) {
            sessions.replace(authentication, PlayerPrincipalMapper.INSTANCE.toPrincipal(legacy), request, response);
        }
        chain.doFilter(request, response);
    }
}
