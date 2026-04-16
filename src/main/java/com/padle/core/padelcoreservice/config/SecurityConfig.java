package com.padle.core.padelcoreservice.config;

import com.padle.core.padelcoreservice.security.CompositeUserDetailsService;
import com.padle.core.padelcoreservice.security.JwtAuthenticationFilter;
import com.padle.core.padelcoreservice.security.RateLimitFilter;
import com.padle.core.padelcoreservice.security.oauth2.CustomOAuth2UserService;
import com.padle.core.padelcoreservice.security.oauth2.OAuth2AuthenticationSuccessHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Import(SecurityBeansConfig.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CompositeUserDetailsService compositeUserDetailsService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index",
                                "/torneos",
                                "/torneo/**",
                                "/login",
                                "/players/registro",
                                "/players/registro/**",
                                "/players/confirmar-email",
                                "/api/auth/**",
                                "/api/players/registro",
                                "/api/players/confirmar-email/**",
                                "/terminos",
                                "/privacidad",
                                "/cookies",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/webjars/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/actuator/**",
                                "/favicon.ico",
                                "/players/confirmar-email",
                                "/players/confirmar-email/**",
                                "/error",
                                "/recuperar-password/solicitar",
                                "/recuperar-password/confirmar",
                                "/recuperar-password",
                                "/recuperar-password/**",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/double-registration/complete"
                        ).permitAll()
                        .requestMatchers("/players/dashboard").authenticated()
                        .requestMatchers("/players/lista").authenticated()
                        .requestMatchers("/players/perfil/**").authenticated()
                        .requestMatchers("/players/mis-torneos").authenticated()
                        .requestMatchers("/admin/**").hasAnyRole("OWNER", "SUPER_ADMIN", "ORGANIZER")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(oAuth2AuthenticationSuccessHandler)
                        .failureUrl("/login?error=oauth2")
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(customAuthenticationSuccessHandler())
                        .failureHandler(customAuthenticationFailureHandler())
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .clearAuthentication(true)
                        .permitAll()
                )
                .rememberMe(remember -> remember
                        .key("uniqueAndSecret")
                        .tokenValiditySeconds(86400)
                        .userDetailsService(compositeUserDetailsService)
                )
                .headers(headers -> headers
                        // Запрет встраивания в iframe (защита от clickjacking)
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        // Запрет определения типа контента браузером
                        .contentTypeOptions(content -> {})
                        // HSTS — только HTTPS (включить когда будет SSL)
                         .httpStrictTransportSecurity(hsts -> hsts
                             .includeSubDomains(true)
                             .maxAgeInSeconds(31536000))
                        // Content Security Policy
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives(
                                        "default-src 'self'; " +
                                                "script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://fonts.googleapis.com https://www.google.com https://www.gstatic.com; " +
                                                "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://fonts.googleapis.com; " +
                                                "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
                                                "img-src 'self' data: https:; " +
                                                "connect-src 'self' https://www.google.com; " +
                                                "frame-src https://www.google.com; " +  // Важно для reCAPTCHA
                                                "frame-ancestors 'none';"
                                )
                        )
                )
                .build();
    }

    @Bean
    public AuthenticationFailureHandler customAuthenticationFailureHandler() {
        return (request, response, exception) -> {
            // Логируем точный тип и сообщение для диагностики
            log.warn("Authentication failure: type={}, message={}",
                    exception.getClass().getName(), exception.getMessage());

            if (exception instanceof DisabledException
                    || exception instanceof org.springframework.security.authentication.LockedException) {
                response.sendRedirect("/login?error=not_confirmed");
            } else {
                response.sendRedirect("/login?error=true");
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:8080", "http://localhost:8081", "http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            String targetUrl = "/players/dashboard";

            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_OWNER") ||
                            auth.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                            auth.getAuthority().equals("ROLE_ORGANIZER"));

            if (isAdmin) {
                targetUrl = "/admin";
            }

            response.sendRedirect(targetUrl);
        };
    }
}