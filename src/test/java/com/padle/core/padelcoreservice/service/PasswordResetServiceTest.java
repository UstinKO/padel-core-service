package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PasswordResetConfirm;
import com.padle.core.persistence.identity.JpaPasswordResetTokens;
import com.padle.core.persistence.identity.PasswordResetToken;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.persistence.identity.PasswordResetTokenRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

// Сквозная проверка сценария восстановления пароля — запрос токена,
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
    @MockitoSpyBean
    private JpaPasswordResetTokens resetTokens;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private EmailService emailService;

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
                .filter(t -> t.getPlayerId().equals(player.getId()))
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
                .playerId(player.getId())
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
                .playerId(player.getId())
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
                .filter(t -> t.getPlayerId().equals(player.getId()))
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
                .filter(t -> t.getPlayerId().equals(player.getId()))
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
        assertThat(masked).isEqualTo("***");
        assertThat(masked).doesNotContain("12345678");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failureAfterFlushRollsBackPasswordAndToken() {
        var player = createPlayer("oldPass123");
        try {
            var token = seedToken(player);
            doAnswer(invocation -> {
                invocation.callRealMethod();
                tokenRepository.flush();
                throw new IllegalStateException("test failure after flush");
            }).when(resetTokens).markUsed(token.getId());

            assertThatThrownBy(() -> passwordResetService.resetPassword(confirm(token, "newPass456")))
                    .isInstanceOf(IllegalStateException.class).hasMessage("test failure after flush");
            assertThat(passwordEncoder.matches("oldPass123",
                    playerRepository.findById(player.getId()).orElseThrow().getPasswordHash())).isTrue();
            assertThat(tokenRepository.findById(token.getId()).orElseThrow().isUsed()).isFalse();
        } finally {
            tokenRepository.deleteByPlayerId(player.getId());
            playerRepository.deleteById(player.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentConfirmationConsumesTokenOnce() throws Exception {
        var player = createPlayer("oldPass123");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var token = seedToken(player);
            var start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                return passwordResetService.resetPassword(confirm(token, "firstPass456"));
            });
            var second = executor.submit(() -> {
                start.await();
                return passwordResetService.resetPassword(confirm(token, "secondPass789"));
            });
            start.countDown();
            boolean firstUsed = first.get(15, TimeUnit.SECONDS);
            boolean secondUsed = second.get(15, TimeUnit.SECONDS);
            assertThat(firstUsed).isNotEqualTo(secondUsed);
            assertThat(tokenRepository.findById(token.getId()).orElseThrow().isUsed()).isTrue();
            assertThat(passwordEncoder.matches(firstUsed ? "firstPass456" : "secondPass789",
                    playerRepository.findById(player.getId()).orElseThrow().getPasswordHash())).isTrue();
        } finally {
            executor.shutdownNow();
            tokenRepository.deleteByPlayerId(player.getId());
            playerRepository.deleteById(player.getId());
        }
    }

    private PasswordResetToken seedToken(PlayerPadel player) {
        return tokenRepository.save(PasswordResetToken.builder().playerId(player.getId())
                .token(UUID.randomUUID().toString()).expiryDate(LocalDateTime.now().plusHours(1)).build());
    }

    private PasswordResetConfirm confirm(PasswordResetToken token, String password) {
        var request = new PasswordResetConfirm();
        request.setToken(token.getToken());
        request.setNewPassword(password);
        request.setConfirmPassword(password);
        return request;
    }
}
