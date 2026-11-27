package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.AccountDtos;
import com.padle.core.padelcoreservice.dto.RegistroRequestDto;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.mapper.AccountMapper;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.application.identity.PlayerProfiles;
import com.padle.core.padelcoreservice.security.PlayerPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {
    private final PlayerProfiles profiles;
    private final PlayerService playerService;
    private final PlayerPhoneGuard phoneGuard;
    private final AccountMapper mapper;

    public PlayerPadel player(Authentication authentication) {
        return playerService.getPlayerById(playerId(authentication))
                .filter(PlayerPadel::isActivo)
                .orElseThrow(() -> new AccountException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED"));
    }

    public AccountDtos.Profile profile(Authentication authentication) {
        return mapper.profile(activeProfile(authentication));
    }

    private PlayerProfiles.Profile activeProfile(Authentication authentication) {
        return profiles.findById(playerId(authentication))
                .filter(PlayerProfiles.Profile::activo)
                .orElseThrow(() -> new AccountException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED"));
    }

    private Long playerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            throw new AccountException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED");
        if (!(authentication.getPrincipal() instanceof PlayerPrincipal player))
            throw new AccountException(HttpStatus.FORBIDDEN, "PLAYER_REQUIRED");
        return player.getId();
    }

    @Transactional
    public void register(RegistroRequestDto request) {
        request.setNombre(request.getNombre().strip());
        request.setApellido(request.getApellido().strip());
        request.setEmail(request.getEmail().strip());
        request.setTelefono(normalize(request.getTelefono()));
        request.setTelegramUsername(telegram(request.getTelegramUsername()));
        if (!request.passwordsMatch()) invalid("PASSWORD_MISMATCH", "confirmPassword");
        duplicates(request.getEmail(), request.getTelegramUsername(), Optional.empty());
        playerService.registrarJugador(request);
    }

    @Transactional
    public AccountDtos.Profile update(Authentication authentication, AccountDtos.ProfileUpdate input) {
        var player = activeProfile(authentication);
        var request = new PlayerProfiles.ProfileUpdate(input.nombre().strip(), input.apellido().strip(), input.email().strip(),
                normalize(input.telefono()), telegram(input.telegramUsername()), input.nivelJugador(), input.preferredLocale());
        if (!Nivel.isPlayerLevel(request.nivelJugador())) invalid("INVALID_FIELD", "nivelJugador");
        if (request.telefono() == null && request.telegramUsername() == null) invalid("CONTACT_REQUIRED", "telefono", "telegramUsername");
        if (!player.isGuestAccount() && !player.email().equals(request.email())) invalid("EMAIL_IMMUTABLE", "email");
        duplicates(request.email(), request.telegramUsername(), Optional.of(player));
        if (!Objects.equals(player.telefono(), request.telefono())) {
            phoneGuard.requireAvailable(request.telefono(), player.id());
        }
        return mapper.profile(profiles.update(player.id(), request));
    }

    @Transactional
    public void updateLevel(Authentication authentication, Nivel nivel) {
        var player = activeProfile(authentication);
        if (!Nivel.isPlayerLevel(nivel)) invalid("INVALID_FIELD", "nivelJugador");
        if (!profiles.updateActiveLevel(player.id(), nivel, LocalDateTime.now()))
            throw new AccountException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED");
    }

    @Transactional
    public void changePassword(Authentication authentication, AccountDtos.PasswordChange request) {
        var player = player(authentication);
        if (!playerService.checkPassword(player, request.currentPassword())) invalid("CURRENT_PASSWORD_INVALID", "currentPassword");
        if (!request.newPassword().equals(request.confirmPassword())) invalid("PASSWORD_MISMATCH", "confirmPassword");
        playerService.cambiarPassword(player.getId(), request.newPassword());
    }

    private void duplicates(String email, String telegram, Optional<PlayerProfiles.Profile> current) {
        if (current.map(player -> !player.email().equals(email)).orElse(true) && profiles.existsByEmail(email))
            invalid("EMAIL_DUPLICATE", "email");
        if (telegram != null && current.map(player -> !telegram.equalsIgnoreCase(Objects.toString(player.telegramUsername(), ""))).orElse(true)
                && profiles.existsByTelegramUsername(telegram)) invalid("TELEGRAM_DUPLICATE", "telegramUsername");
    }

    private static String normalize(String value) {
        // JPA хранит отсутствующий контакт как SQL NULL.
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String telegram(String value) {
        var normalized = normalize(value);
        return normalized == null || normalized.startsWith("@") ? normalized : "@" + normalized;
    }

    private static void invalid(String code, String... fields) {
        throw new AccountException(HttpStatus.UNPROCESSABLE_ENTITY, code, fields);
    }
}
