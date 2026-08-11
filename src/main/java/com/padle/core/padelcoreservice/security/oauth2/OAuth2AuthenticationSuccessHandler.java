package com.padle.core.padelcoreservice.security.oauth2;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.rememberme.AbstractRememberMeServices;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final RememberMeServices rememberMeServices;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        PlayerPadel player = oAuth2User.getPlayer();

        log.info("OAuth2 login exitoso para: {} (ID: {})", player.getEmail(), player.getId());

        // Устанавливаем аутентификацию в SecurityContext (важно!)
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // У кнопки "Войти через Google" нет и не может быть чекбокса "запомнить меня" — логин
        // завершается редиректом от самого Google, не нашей формой, поэтому параметра
        // remember-me, который проверяет AbstractRememberMeServices.rememberMeRequested(),
        // взять неоткуда. Считаем "Войти через Google" неявным "запомнить меня" — подставляем
        // параметр сами через обёртку запроса (issue #297: раньше remember-me cookie для
        // OAuth2-логина не выставлялась вообще никогда, и сессия не переживала закрытие браузера).
        HttpServletRequest requestWithRememberMe = new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                if (AbstractRememberMeServices.DEFAULT_PARAMETER.equals(name)) {
                    return "true";
                }
                return super.getParameter(name);
            }
        };
        rememberMeServices.loginSuccess(requestWithRememberMe, response, authentication);

        // Редиректим на dashboard
        getRedirectStrategy().sendRedirect(request, response, "/players/dashboard");
    }
}