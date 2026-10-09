package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PasswordResetConfirm;
import com.padle.core.padelcoreservice.logging.AlertSeverity;
import com.padle.core.padelcoreservice.logging.StructuredAlert;
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
            // LFPT-0518: email — на info (виден в файловых логах/Loki, там он маскируется
            // %mask-паттерном, см. logging/MaskingConverter), в Telegram-алерт не попадает —
            // только безопасный статус этапа, без PII.
            log.info("Intento de restablecimiento para email no registrado: {}", email);
            StructuredAlert.of(AlertSeverity.USER_VALIDATION_ERROR, "Ошибка восстановления пароля", "PASSWORD_RESET_REQUEST")
                    .field("Result", "email_not_found")
                    .warn(log);
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

        String resetUrl = baseUrl + "/recuperar-password?token=" + token;
        log.info("Reset URL generado para playerId={}: {}", player.getId(), resetUrl);
        emailService.sendPasswordResetEmail(player.getEmail(), player.getNombre(), resetUrl, player.getLocale());

        log.info("Email de restablecimiento enviado a: {}", email);
        return true;
    }

    /**
     * Проверка валидности токена
     */
    public boolean validateToken(String token) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token).orElse(null);

        if (resetToken == null) {
            log.info("Token de recuperación no encontrado: {}", maskToken(token));
            alertTokenInvalid("invalid", null);
            return false;
        }
        if (resetToken.isUsed()) {
            log.info("Token de recuperación ya utilizado: {}, playerId={}",
                    maskToken(token), resetToken.getPlayer().getId());
            alertTokenInvalid("already_used", resetToken.getPlayer().getId());
            return false;
        }
        if (resetToken.isExpired()) {
            log.info("Token de recuperación expirado: {}, playerId={}, expiryDate={}",
                    maskToken(token), resetToken.getPlayer().getId(), resetToken.getExpiryDate());
            alertTokenInvalid("expired", resetToken.getPlayer().getId());
            return false;
        }
        return true;
    }

    /**
     * LFPT-0518: событие PASSWORD_RESET_TOKEN_INVALID для Telegram-алерта — только статус
     * токена (valid/expired/already_used/invalid), без самого токена и без ссылки (это
     * одноразовый секрет для сброса пароля). Подробности с замаскированным токеном — отдельно
     * на info, в файловых логах/Loki (см. вызовы выше и в resetPassword).
     */
    private void alertTokenInvalid(String tokenStatus, Long playerId) {
        StructuredAlert.of(AlertSeverity.USER_VALIDATION_ERROR, "Ошибка восстановления пароля", "PASSWORD_RESET_TOKEN_INVALID")
                .field("Token status", tokenStatus)
                .field("User ID", playerId)
                .warn(log);
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

        if (resetToken == null) {
            log.info("Intento de restablecimiento con token no encontrado: {}", maskToken(confirmDto.getToken()));
            alertTokenInvalid("invalid", null);
            return false;
        }
        if (resetToken.isUsed()) {
            log.info("Intento de restablecimiento con token ya utilizado: {}, playerId={}",
                    maskToken(confirmDto.getToken()), resetToken.getPlayer().getId());
            alertTokenInvalid("already_used", resetToken.getPlayer().getId());
            return false;
        }
        if (resetToken.isExpired()) {
            log.info("Intento de restablecimiento con token expirado: {}, playerId={}, expiryDate={}",
                    maskToken(confirmDto.getToken()), resetToken.getPlayer().getId(), resetToken.getExpiryDate());
            alertTokenInvalid("expired", resetToken.getPlayer().getId());
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

    /**
     * Токен — одноразовый секрет для сброса пароля: в логах показываем только
     * первые символы, чтобы диагностировать проблему без раскрытия всего значения.
     */
    public static String maskToken(String token) {
        if (token == null) {
            return "null";
        }
        return token.length() > 8 ? token.substring(0, 8) + "..." : "***";
    }
}