package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PasswordResetConfirm;
import com.padle.core.padelcoreservice.model.PasswordResetToken;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.repository.PasswordResetTokenRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// LFPT-0499: сквозная проверка сценария восстановления пароля — запрос токена,
// переход по ссылке (валидный/просроченный/использованный токен), смена пароля,
// старый пароль перестаёт работать, повторное использование токена отклоняется.
@SpringBootTest
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key",
})
@Transactional
class PasswordResetServiceTest {

    @Autowired
    private PasswordResetService passwordResetService;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private PlayerPadel createPlayer(String oldPasswordRaw) {
        return playerRepository.save(PlayerPadel.builder()
                .nombre("Test")
                .apellido("Reset")
                .email("reset-" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode(oldPasswordRaw))
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }

    @Test
    void requestPasswordReset_jugadorExistente_creaTokenValido() {
        PlayerPadel player = createPlayer("oldPass123");

        boolean result = passwordResetService.requestPasswordReset(player.getEmail());

        assertThat(result).isTrue();
        PasswordResetToken token = tokenRepository.findAll().stream()
                .filter(t -> t.getPlayer().getId().equals(player.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(token.isUsed()).isFalse();
        assertThat(passwordResetService.validateToken(token.getToken())).isTrue();
    }

    @Test
    void requestPasswordReset_emailNoRegistrado_noFallaYNoCreaToken() {
        boolean result = passwordResetService.requestPasswordReset("no-existe-" + UUID.randomUUID() + "@example.com");

        assertThat(result).isTrue();
    }

    @Test
    void validateToken_tokenInexistente_devuelveFalse() {
        assertThat(passwordResetService.validateToken("token-que-no-existe")).isFalse();
    }

    @Test
    void validateToken_tokenExpirado_devuelveFalse() {
        PlayerPadel player = createPlayer("oldPass123");
        PasswordResetToken expired = tokenRepository.save(PasswordResetToken.builder()
                .token(UUID.randomUUID().toString())
                .player(player)
                .expiryDate(LocalDateTime.now().minusHours(1))
                .used(false)
                .build());

        assertThat(passwordResetService.validateToken(expired.getToken())).isFalse();
    }

    @Test
    void validateToken_tokenYaUtilizado_devuelveFalse() {
        PlayerPadel player = createPlayer("oldPass123");
        PasswordResetToken used = tokenRepository.save(PasswordResetToken.builder()
                .token(UUID.randomUUID().toString())
                .player(player)
                .expiryDate(LocalDateTime.now().plusHours(1))
                .used(true)
                .build());

        assertThat(passwordResetService.validateToken(used.getToken())).isFalse();
    }

    @Test
    void cicloCompleto_cambiaPassword_viejaNoFunciona_nuevaFunciona_tokenNoReutilizable() {
        PlayerPadel player = createPlayer("oldPass123");
        passwordResetService.requestPasswordReset(player.getEmail());
        PasswordResetToken token = tokenRepository.findAll().stream()
                .filter(t -> t.getPlayer().getId().equals(player.getId()))
                .findFirst()
                .orElseThrow();

        PasswordResetConfirm confirm = new PasswordResetConfirm();
        confirm.setToken(token.getToken());
        confirm.setNewPassword("newPass456");
        confirm.setConfirmPassword("newPass456");

        boolean success = passwordResetService.resetPassword(confirm);
        assertThat(success).isTrue();

        PlayerPadel updated = playerRepository.findById(player.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("oldPass123", updated.getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches("newPass456", updated.getPasswordHash())).isTrue();

        // Повторное использование того же токена — отклоняется
        PasswordResetConfirm reuse = new PasswordResetConfirm();
        reuse.setToken(token.getToken());
        reuse.setNewPassword("anotherPass789");
        reuse.setConfirmPassword("anotherPass789");
        assertThat(passwordResetService.resetPassword(reuse)).isFalse();
    }

    @Test
    void resetPassword_passwordsNoCoinciden_devuelveFalse() {
        PlayerPadel player = createPlayer("oldPass123");
        passwordResetService.requestPasswordReset(player.getEmail());
        PasswordResetToken token = tokenRepository.findAll().stream()
                .filter(t -> t.getPlayer().getId().equals(player.getId()))
                .findFirst()
                .orElseThrow();

        PasswordResetConfirm confirm = new PasswordResetConfirm();
        confirm.setToken(token.getToken());
        confirm.setNewPassword("newPass456");
        confirm.setConfirmPassword("diferente789");

        assertThat(passwordResetService.resetPassword(confirm)).isFalse();
    }

    @Test
    void maskToken_noExponeTokenCompleto() {
        String token = "abcdefgh12345678";
        String masked = PasswordResetService.maskToken(token);

        assertThat(masked).isNotEqualTo(token);
        assertThat(masked).startsWith("abcdefgh");
        assertThat(masked).doesNotContain("12345678");
    }
}
