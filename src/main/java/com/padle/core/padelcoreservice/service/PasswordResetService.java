package com.padle.core.padelcoreservice.service;

import com.padle.core.application.identity.PasswordResetTokens;
import com.padle.core.padelcoreservice.dto.PasswordResetConfirm;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.security.AccountDestination;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {
    private final PlayerRepository playerRepository;
    private final PasswordResetTokens tokens;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    @Value("${app.base-url}")
    private String baseUrl;
    private static final int TOKEN_EXPIRATION_HOURS = 24;

    @Transactional
    public boolean requestPasswordReset(String email) {
        return requestPasswordReset(email, Optional.empty());
    }

    @Transactional
    public boolean requestPasswordReset(String email, Optional<String> destination) {
        playerRepository.findByEmail(email).filter(PlayerPadel::isActivo)
                .filter(player -> !player.isGuestAccount()).ifPresent(player -> {
            tokens.revokeForPlayer(player.getId());
            String token = UUID.randomUUID().toString();
            tokens.issue(player.getId(), token, LocalDateTime.now().plusHours(TOKEN_EXPIRATION_HOURS));
            String url = baseUrl + "/recuperar-password?token=" + token
                    + destination.flatMap(AccountDestination::safe)
                    .map(value -> "&destination=" + URLEncoder.encode(value, StandardCharsets.UTF_8)).orElse("");
            emailService.sendPasswordResetEmail(player.getEmail(), player.getNombre(), url, player.getLocale());
        });
        return true;
    }

    public boolean validateToken(String token) {
        return tokens.findByToken(token).flatMap(this::eligiblePlayer).isPresent();
    }

    @Transactional
    public boolean resetPassword(PasswordResetConfirm request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) return false;
        return tokens.lockByToken(request.getToken()).flatMap(token -> eligiblePlayer(token).map(player -> {
            player.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            playerRepository.save(player);
            tokens.markUsed(token.id());
            return true;
        })).orElse(false);
    }

    private Optional<PlayerPadel> eligiblePlayer(PasswordResetTokens.Token token) {
        if (token.used() || LocalDateTime.now().isAfter(token.expiryDate())) {
            return Optional.empty();
        }
        return playerRepository.findById(token.playerId()).filter(PlayerPadel::isActivo)
                .filter(player -> !player.isGuestAccount());
    }

    @Transactional
    public void cleanExpiredTokens() {
        tokens.deleteExpiredOrUsed(LocalDateTime.now());
    }

    public static String maskToken(String token) { return "***"; }
}
