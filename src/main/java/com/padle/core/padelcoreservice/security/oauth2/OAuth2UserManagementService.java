package com.padle.core.padelcoreservice.security.oauth2;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserManagementService {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder; // Теперь нет цикла!
    private static final SecureRandom secureRandom = new SecureRandom();

    private String generateSecureRandomPassword() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    @Transactional
    public PlayerPadel createOrUpdateUser(String email, String firstName, String lastName, String provider) {
        Optional<PlayerPadel> existingUser = playerRepository.findByEmail(email);

        if (existingUser.isPresent()) {
            PlayerPadel player = existingUser.get();
            log.info("Usuario existente con email: {}, actualizando datos", email);

            if (!player.isEmailConfirmado()) {
                player.setEmailConfirmado(true);
                player.setFechaConfirmacionEmail(LocalDateTime.now());
            }

            if (player.getProvider() == null) {
                player.setProvider(provider);
            }

            if (!player.isOauth2User()) {
                player.setOauth2User(true);
            }

            return playerRepository.save(player);
        } else {
            log.info("Creando nuevo usuario con email: {} via {}", email, provider);

            String rawPassword = generateSecureRandomPassword();
            String encodedPassword = passwordEncoder.encode(rawPassword);

            log.debug("Usuario OAuth2 {} - пароль сгенерирован и захэширован", email);

            PlayerPadel newPlayer = PlayerPadel.builder()
                    .email(email)
                    .nombre(firstName)
                    .apellido(lastName)
                    .passwordHash(encodedPassword)
                    .emailConfirmado(true)
                    .fechaConfirmacionEmail(LocalDateTime.now())
                    .activo(true)
                    .provider(provider)
                    .oauth2User(true)
                    .passwordChangedAt(LocalDateTime.now())
                    .build();

            return playerRepository.save(newPlayer);
        }
    }
}