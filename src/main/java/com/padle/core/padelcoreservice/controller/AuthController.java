package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.AuthRequest;
import com.padle.core.padelcoreservice.dto.AuthResponse;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.security.JwtService;
import com.padle.core.padelcoreservice.security.PlayerUserService;
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

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final PlayerUserService playerUserService;
    private final JwtService jwtService;
    private final PlayerRepository playerRepository;

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

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            Optional<PlayerPadel> playerOpt = playerRepository.findByEmail(userDetails.getUsername());

            if (playerOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Usuario no encontrado");
            }

            PlayerPadel player = playerOpt.get();

            // Verificar que el email esté confirmado
            if (!player.isEmailConfirmado()) {
                return ResponseEntity.badRequest().body("Por favor, confirma tu email primero");
            }

            // Verificar que el usuario esté activo
            if (!player.isActivo()) {
                return ResponseEntity.badRequest().body("Tu cuenta está desactivada");
            }

            String accessToken = jwtService.generateToken(
                    userDetails,
                    player.getId(),
                    player.getNombreCompleto()
            );

            String refreshToken = jwtService.generateRefreshToken(
                    userDetails,
                    player.getId()
            );

            AuthResponse response = AuthResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .email(player.getEmail())
                    .nombreCompleto(player.getNombreCompleto())
                    .playerId(player.getId())
                    .expiresIn(86400000L) // 24 horas
                    .build();

            log.info("User {} authenticated successfully", player.getEmail());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Authentication failed for user {}: {}", request.getEmail(), e.getMessage());
            return ResponseEntity.badRequest().body("Email o contraseña incorrectos");
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestParam String refreshToken) {
        log.info("Refreshing token");

        try {
            if (!jwtService.isRefreshToken(refreshToken)) {
                return ResponseEntity.badRequest().body("Token de refresco inválido");
            }

            String email = jwtService.extractUsername(refreshToken);
            UserDetails userDetails = playerUserService.loadUserByUsername(email);

            if (!jwtService.validateToken(refreshToken, userDetails)) {
                return ResponseEntity.badRequest().body("Token de refresco expirado o inválido");
            }

            Optional<PlayerPadel> playerOpt = playerRepository.findByEmail(email);
            if (playerOpt.isEmpty()) {
                return ResponseEntity.badRequest().body("Usuario no encontrado");
            }

            PlayerPadel player = playerOpt.get();

            String newAccessToken = jwtService.generateToken(
                    userDetails,
                    player.getId(),
                    player.getNombreCompleto()
            );

            String newRefreshToken = jwtService.generateRefreshToken(
                    userDetails,
                    player.getId()
            );

            AuthResponse response = AuthResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .email(player.getEmail())
                    .nombreCompleto(player.getNombreCompleto())
                    .playerId(player.getId())
                    .expiresIn(86400000L)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Error al refrescar el token");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body("No autenticado");
        }

        String email = authentication.getName();
        Optional<PlayerPadel> playerOpt = playerRepository.findByEmail(email);

        if (playerOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Usuario no encontrado");
        }

        PlayerPadel player = playerOpt.get();

        return ResponseEntity.ok(player);
    }
}