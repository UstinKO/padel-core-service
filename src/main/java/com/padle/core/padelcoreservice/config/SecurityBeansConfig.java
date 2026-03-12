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

@Configuration
@RequiredArgsConstructor
public class SecurityBeansConfig {

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
        return new OAuth2AuthenticationSuccessHandler(jwtService);
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