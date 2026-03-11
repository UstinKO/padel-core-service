package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PasswordResetConfirm;
import com.padle.core.padelcoreservice.model.PasswordResetToken;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.PasswordResetTokenRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final PlayerRepository playerRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${app.base-url}")
    private String baseUrl;

    private static final int TOKEN_EXPIRATION_HOURS = 24;

    /**
     * Запрос на сброс пароля
     */
    @Transactional
    public boolean requestPasswordReset(String email) {
        log.info("Solicitud de restablecimiento de contraseña para email: {}", email);

        PlayerPadel player = playerRepository.findByEmail(email)
                .orElse(null);

        // Всегда возвращаем true, даже если email не найден (безопасность)
        if (player == null) {
            log.warn("Intento de restablecimiento para email no registrado: {}", email);
            return true;
        }

        // Удаляем старые токены
        tokenRepository.deleteByPlayer(player);

        // Создаем новый токен
        String token = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .player(player)
                .expiryDate(LocalDateTime.now().plusHours(TOKEN_EXPIRATION_HOURS))
                .used(false)
                .build();

        tokenRepository.save(resetToken);

        // ИСПРАВЛЕНО: /recuperar-password вместо /reset-password
        String resetUrl = baseUrl + "/recuperar-password?token=" + token;
        emailService.sendPasswordResetEmail(player.getEmail(), player.getNombre(), resetUrl);

        log.info("Email de restablecimiento enviado a: {}", email);
        return true;
    }

    /**
     * Проверка валидности токена
     */
    public boolean validateToken(String token) {
        return tokenRepository.findByToken(token)
                .map(t -> !t.isUsed() && !t.isExpired())
                .orElse(false);
    }

    /**
     * Сброс пароля
     */
    @Transactional
    public boolean resetPassword(PasswordResetConfirm confirmDto) {
        log.info("Procesando restablecimiento de contraseña");

        if (!confirmDto.getNewPassword().equals(confirmDto.getConfirmPassword())) {
            log.warn("Las contraseñas no coinciden");
            return false;
        }

        PasswordResetToken resetToken = tokenRepository.findByToken(confirmDto.getToken())
                .orElse(null);

        if (resetToken == null || resetToken.isUsed() || resetToken.isExpired()) {
            log.warn("Token inválido, usado o expirado");
            return false;
        }

        PlayerPadel player = resetToken.getPlayer();
        player.setPasswordHash(passwordEncoder.encode(confirmDto.getNewPassword()));
        playerRepository.save(player);

        // Помечаем токен как использованный
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        log.info("Contraseña restablecida exitosamente para usuario: {}", player.getEmail());
        return true;
    }

    /**
     * Очистка старых токенов (можно запускать по расписанию)
     */
    @Transactional
    public void cleanExpiredTokens() {
        tokenRepository.deleteAllExpiredOrUsed(LocalDateTime.now());
        log.info("Tokens expirados eliminados");
    }
}