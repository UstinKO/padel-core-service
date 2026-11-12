package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PasswordResetConfirm;
import com.padle.core.padelcoreservice.model.PasswordResetToken;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.PasswordResetTokenRepository;
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
    private final PasswordResetTokenRepository tokenRepository;
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
            tokenRepository.deleteByPlayer(player);
            String token = UUID.randomUUID().toString();
            tokenRepository.save(PasswordResetToken.builder().token(token).player(player)
                    .expiryDate(LocalDateTime.now().plusHours(TOKEN_EXPIRATION_HOURS)).used(false).build());
            String url = baseUrl + "/recuperar-password?token=" + token
                    + destination.flatMap(AccountDestination::safe)
                    .map(value -> "&destination=" + URLEncoder.encode(value, StandardCharsets.UTF_8)).orElse("");
            emailService.sendPasswordResetEmail(player.getEmail(), player.getNombre(), url, player.getLocale());
        });
        return true;
    }

    public boolean validateToken(String token) {
        return tokenRepository.findByToken(token).filter(this::usable).isPresent();
    }

    @Transactional
    public boolean resetPassword(PasswordResetConfirm request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) return false;
        var token = tokenRepository.findForUpdateByToken(request.getToken()).filter(this::usable);
        if (token.isEmpty()) return false;
        var reset = token.orElseThrow();
        var player = reset.getPlayer();
        player.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        playerRepository.save(player);
        reset.setUsed(true);
        tokenRepository.save(reset);
        return true;
    }

    private boolean usable(PasswordResetToken token) { return !token.isUsed() && !token.isExpired()
            && token.getPlayer().isActivo() && !token.getPlayer().isGuestAccount(); }

    @Transactional
    public void cleanExpiredTokens() { tokenRepository.deleteAllExpiredOrUsed(LocalDateTime.now()); }

    public static String maskToken(String token) { return "***"; }
}
