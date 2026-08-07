package com.padle.core.padelcoreservice.config;

import com.padle.core.padelcoreservice.security.CompositeUserDetailsService;
import com.padle.core.padelcoreservice.security.CustomAccessDeniedHandler;
import com.padle.core.padelcoreservice.security.JwtAuthenticationFilter;
import com.padle.core.padelcoreservice.security.RateLimitFilter;
import com.padle.core.padelcoreservice.security.oauth2.CustomOAuth2UserService;
import com.padle.core.padelcoreservice.security.oauth2.OAuth2AuthenticationSuccessHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
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
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // Дефолтный XorCsrfTokenRequestAttributeHandler маскирует токен (BREACH-защита)
                        // для форм, рендерящих ${_csrf.token} на сервере. Но JS в csrf.js читает "сырое"
                        // значение напрямую из cookie XSRF-TOKEN и шлёт его как есть в заголовке — с Xor-
                        // обработчиком такое сравнение не пройдёт (сервер ждёт маскированное значение).
                        // Используем немаскирующий обработчик — стандартная рекомендация Spring Security
                        // для cookie-based CSRF, читаемого через JS.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Bearer JWT (заголовок Authorization) не подвержен CSRF — его нельзя
                        // выставить форгед cross-site запросом, в отличие от session-cookie.
                        // Игнорируем такие запросы, чтобы не ломать stateless API-клиентов.
                        .ignoringRequestMatchers(request -> request.getHeader("Authorization") != null)
                        // #284: SockJS-фоллбэк транспорты (xhr/xhr_streaming — для клиентов без
                        // нативного WebSocket) шлют служебные POST без X-XSRF-TOKEN — это не fetch,
                        // csrf.js их не видит. Канал только для broadcast сервер→клиент (нет ни одного
                        // @MessageMapping), все мутации идут через отдельные CSRF-защищённые /api/**.
                        .ignoringRequestMatchers("/api/auth/**", "/test/telegram/**", "/ws/**")
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index",
                                "/torneos",
                                "/torneo/**",
                                "/clubs",
                                "/clubs/**",
                                "/ranking",
                                "/tournaments/americano/*",
                                "/tournaments/americano/*/ranking",
                                "/tournaments/team-americano/*",
                                "/tournaments/team-americano/*/ranking",
                                "/tournaments/team-playoff/*",
                                "/waitlist/**",
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
                                "/legal/**",
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
                                "/error/rate-limit",
                                "/error/403",
                                "/recuperar-password/solicitar",
                                "/recuperar-password/confirmar",
                                "/recuperar-password",
                                "/recuperar-password/**",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/double-registration/complete",
                                "/double-registration/accept-pair",
                                "/ws/**",
                                "/test/telegram/**",
                                "/api/cookies/**"
                        ).permitAll()
                        .requestMatchers("/players/dashboard").authenticated()
                        .requestMatchers("/players/lista").authenticated()
                        .requestMatchers("/players/perfil/**").authenticated()
                        .requestMatchers("/players/mis-torneos").authenticated()
                        .requestMatchers("/admin/**").hasAnyRole("OWNER", "SUPER_ADMIN", "ORGANIZER", "ADMIN")
                        // Внутренний тестовый инструментарий организатора (JDBC-based, см. CLAUDE.md) —
                        // доступен только владельцу платформы, не ORGANIZER/ADMIN.
                        .requestMatchers("/test/tournaments/**").hasRole("SUPER_ADMIN")
                        // Публичные GET-эндпоинты King of Court для страницы зрителей
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/king-of-court/tournaments/*/state",
                                "/api/king-of-court/tournaments/*/ranking",
                                "/api/king-of-court/tournaments/*/players/*/history",
                                "/api/king-of-court/ping",
                                "/api/tournaments/double/*/looking-for-partner"
                        ).permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/tournaments/double/accept-pair"
                        ).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                // Spring Security 6 генерирует CSRF-токен лениво: cookie XSRF-TOKEN пишется,
                // только если токен реально прочитан за время запроса (обычно — рендером
                // th:action формы). Страницы без форм (чисто JS/fetch) иначе останутся без
                // cookie. Форсируем чтение токена на каждом запросе.
                .addFilterAfter(csrfCookieFilter(), CsrfFilter.class)
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
                                                "script-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://fonts.googleapis.com https://www.google.com https://www.gstatic.com https://cdn.jsdelivr.net; " +
                                                "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://fonts.googleapis.com https://cdn.jsdelivr.net; " +
                                                "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; " +
                                                "img-src 'self' data: https:; " +
                                                "connect-src 'self' https://www.google.com; " +
                                                "frame-src https://www.google.com; " +
                                                "frame-ancestors 'none';"
                                )
                        )
                )
                .build();
    }

    @Bean
    public OncePerRequestFilter csrfCookieFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                             HttpServletResponse response,
                                             FilterChain filterChain)
                    throws ServletException, IOException {
                CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                if (csrfToken != null) {
                    csrfToken.getToken();
                }
                filterChain.doFilter(request, response);
            }
        };
    }

    @Bean
    public AuthenticationFailureHandler customAuthenticationFailureHandler() {
        return (request, response, exception) -> {
            log.info("Authentication failure: type={}, message={}",
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
        configuration.setAllowedOrigins(List.of(
                "http://localhost:8080", "http://localhost:8081", "http://localhost:3000",
                "https://epadel.org", "https://www.epadel.org",
                "https://1-padel.com", "https://www.1-padel.com"));
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
                            auth.getAuthority().equals("ROLE_ORGANIZER") ||
                            auth.getAuthority().equals("ROLE_ADMIN"));

            if (isAdmin) {
                targetUrl = "/admin";
            }

            response.sendRedirect(targetUrl);
        };
    }
}