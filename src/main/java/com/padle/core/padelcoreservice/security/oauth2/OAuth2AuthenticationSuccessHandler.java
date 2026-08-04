package com.padle.core.padelcoreservice.security.oauth2;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        PlayerPadel player = oAuth2User.getPlayer();

        log.info("OAuth2 login exitoso para: {} (ID: {})", player.getEmail(), player.getId());

        // Устанавливаем аутентификацию в SecurityContext (важно!)
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Редиректим на dashboard
        getRedirectStrategy().sendRedirect(request, response, "/players/dashboard");
    }
}