package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.AuthRequest;
import com.padle.core.padelcoreservice.dto.AuthResponse;
import com.padle.core.padelcoreservice.dto.RefreshTokenRequest;
import com.padle.core.padelcoreservice.mapper.OwnerMapper;
import com.padle.core.padelcoreservice.mapper.PlayerMapper;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.security.CompositeUserDetailsService;
import com.padle.core.padelcoreservice.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Owner (SUPER_ADMIN/ORGANIZER/ADMIN) и PlayerPadel — оба возможные принципалы
 * (issue #124): CompositeUserDetailsService уже умеет резолвить оба типа, и
 * JwtService/JwtAuthenticationFilter уже полностью generic — этот контроллер
 * был единственным местом, которое смотрело только в PlayerRepository напрямую.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final CompositeUserDetailsService compositeUserDetailsService;
    private final JwtService jwtService;
    private final PlayerMapper playerMapper;
    private final OwnerMapper ownerMapper;

    @PostMapping("/login")
    public ResponseEntity<?> authenticate(@Valid @RequestBody AuthRequest request) {
        log.info("Attempting authentication for user: {}", request.getEmail());

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Уже резолвлен CompositeUserDetailsService-ом внутри authenticate() —
            // повторный поход в репозиторий не нужен.
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();

            if (userDetails instanceof PlayerPadel player) {
                if (!player.isEmailConfirmado()) {
                    return ResponseEntity.badRequest().body("Por favor, confirma tu email primero");
                }
                if (!player.isActivo()) {
                    return ResponseEntity.badRequest().body("Tu cuenta está desactivada");
                }
            } else if (!(userDetails instanceof Owner)) {
                return ResponseEntity.badRequest().body("Usuario no encontrado");
            }

            AuthResponse response = buildAuthResponse(userDetails);
            log.info("User {} authenticated successfully", request.getEmail());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Authentication failed for user {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.badRequest().body("Email o contraseña incorrectos");
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Refreshing token");
        String refreshToken = request.getRefreshToken();

        try {
            if (!jwtService.isRefreshToken(refreshToken)) {
                return ResponseEntity.badRequest().body("Token de refresco inválido");
            }

            String email = jwtService.extractUsername(refreshToken);
            UserDetails userDetails = compositeUserDetailsService.loadUserByUsername(email);

            if (!jwtService.validateToken(refreshToken, userDetails)) {
                return ResponseEntity.badRequest().body("Token de refresco expirado o inválido");
            }

            return ResponseEntity.ok(buildAuthResponse(userDetails));

        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Error al refrescar el token");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // authentication.isAuthenticated() всегда true и для AnonymousAuthenticationToken —
        // проверяем конкретный тип принципала, а не этот флаг (см. issue #122).
        Object principal = authentication != null ? authentication.getPrincipal() : null;

        if (principal instanceof PlayerPadel player) {
            return ResponseEntity.ok(playerMapper.entityToResponse(player));
        }
        if (principal instanceof Owner owner) {
            return ResponseEntity.ok(ownerMapper.toDto(owner));
        }

        return ResponseEntity.status(401).body("No autenticado");
    }

    private AuthResponse buildAuthResponse(UserDetails userDetails) {
        Long id;
        String fullName;
        String email;

        if (userDetails instanceof PlayerPadel player) {
            id = player.getId();
            fullName = player.getNombreCompleto();
            email = player.getEmail();
        } else if (userDetails instanceof Owner owner) {
            id = owner.getId();
            fullName = owner.getFullName();
            email = owner.getEmail();
        } else {
            throw new IllegalStateException("Unknown principal type: " + userDetails.getClass());
        }

        return AuthResponse.builder()
                .accessToken(jwtService.generateToken(userDetails, id, fullName))
                .refreshToken(jwtService.generateRefreshToken(userDetails, id))
                .email(email)
                .nombreCompleto(fullName)
                .playerId(id)
                .expiresIn(jwtService.getAccessTokenExpirationMs())
                .build();
    }
}
