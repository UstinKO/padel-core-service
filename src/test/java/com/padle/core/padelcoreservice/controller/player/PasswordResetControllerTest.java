package com.padle.core.padelcoreservice.controller.player;

import com.padle.core.padelcoreservice.model.PasswordResetToken;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.repository.PasswordResetTokenRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// LFPT-0499: GET /recuperar-password con token inválido/expirado/usado ya no debe
// devolver la view "error" inexistente (templates/error.html no existe a nivel raíz,
// causaba TemplateInputException / 500) — debe redirigir a /login?error=reset_expired.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key",
})
@Transactional
class PasswordResetControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PasswordResetTokenRepository tokenRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private PlayerPadel createPlayer() {
        return playerRepository.save(PlayerPadel.builder()
                .nombre("Test")
                .apellido("Reset")
                .email("reset-ctrl-" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("oldPass123"))
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }

    // Cada test usa una X-Forwarded-For distinta: /recuperar-password comparte el
    // bucket de rate-limit "REGISTER" (3 solicitudes/min por IP, RateLimitFilter) con
    // el resto de la clase — sin esto, el 4º test de la clase recibe 429 en vez de 302.

    @Test
    void tokenInexistente_redirigeALoginConResetExpired_sinError500() throws Exception {
        mockMvc.perform(get("/recuperar-password")
                        .param("token", "no-existe")
                        .header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("http://**/login?error=reset_expired"));
    }

    @Test
    void tokenExpirado_redirigeALoginConResetExpired() throws Exception {
        PlayerPadel player = createPlayer();
        PasswordResetToken expired = tokenRepository.save(PasswordResetToken.builder()
                .token(UUID.randomUUID().toString())
                .player(player)
                .expiryDate(LocalDateTime.now().minusMinutes(1))
                .used(false)
                .build());

        mockMvc.perform(get("/recuperar-password")
                        .param("token", expired.getToken())
                        .header("X-Forwarded-For", "10.0.0.2"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("http://**/login?error=reset_expired"));
    }

    @Test
    void tokenYaUtilizado_redirigeALoginConResetExpired() throws Exception {
        PlayerPadel player = createPlayer();
        PasswordResetToken used = tokenRepository.save(PasswordResetToken.builder()
                .token(UUID.randomUUID().toString())
                .player(player)
                .expiryDate(LocalDateTime.now().plusHours(1))
                .used(true)
                .build());

        mockMvc.perform(get("/recuperar-password")
                        .param("token", used.getToken())
                        .header("X-Forwarded-For", "10.0.0.3"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("http://**/login?error=reset_expired"));
    }

    @Test
    void tokenValido_redirigeAHomeConToken() throws Exception {
        PlayerPadel player = createPlayer();
        PasswordResetToken valid = tokenRepository.save(PasswordResetToken.builder()
                .token(UUID.randomUUID().toString())
                .player(player)
                .expiryDate(LocalDateTime.now().plusHours(1))
                .used(false)
                .build());

        mockMvc.perform(get("/recuperar-password")
                        .param("token", valid.getToken())
                        .header("X-Forwarded-For", "10.0.0.4"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("http://**/?token=" + valid.getToken()));
    }
}
