package com.padle.core.padelcoreservice.config;

import com.padle.core.padelcoreservice.security.*;
import com.padle.core.padelcoreservice.security.oauth2.CustomOAuth2UserService;
import com.padle.core.padelcoreservice.security.oauth2.OAuth2AuthenticationSuccessHandler;
import com.padle.core.padelcoreservice.security.oauth2.OAuth2UserManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

@Configuration
@RequiredArgsConstructor
public class SecurityBeansConfig {

    // Должен совпадать с ключом, которым RememberMeAuthenticationFilter проверяет подпись
    // cookie на последующих запросах — оба берут этот же бин RememberMeServices.
    private static final String REMEMBER_ME_KEY = "uniqueAndSecret";
    private static final int REMEMBER_ME_VALIDITY_SECONDS = 60 * 60 * 24 * 30; // 30 дней

    private final PlayerUserService playerUserService;
    private final OwnerUserService ownerUserService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2UserManagementService oAuth2UserManagementService; // Добавляем

    @Bean
    @Primary // Помечаем как основной
    public UserDetailsService compositeUserDetailsService() {
        return new CompositeUserDetailsService(playerUserService, ownerUserService);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService, compositeUserDetailsService());
    }

    @Bean
    public CustomOAuth2UserService customOAuth2UserService() {
        return new CustomOAuth2UserService(oAuth2UserManagementService); // Передаем зависимость
    }

    @Bean
    public OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler() {
        return new OAuth2AuthenticationSuccessHandler(rememberMeServices());
    }

    // #297: общий на форму-логин и Google OAuth2 — единственный источник remember-me
    // cookie в приложении, чтобы RememberMeAuthenticationFilter (проверка на следующих
    // запросах) и OAuth2AuthenticationSuccessHandler (явная выдача после Google-логина)
    // работали с одним и тем же ключом/сроком действия.
    @Bean
    public RememberMeServices rememberMeServices() {
        TokenBasedRememberMeServices services =
                new TokenBasedRememberMeServices(REMEMBER_ME_KEY, compositeUserDetailsService());
        services.setTokenValiditySeconds(REMEMBER_ME_VALIDITY_SECONDS);
        return services;
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(compositeUserDetailsService());
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(false);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}