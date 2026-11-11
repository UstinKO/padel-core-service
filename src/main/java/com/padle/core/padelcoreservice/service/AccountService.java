package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.AccountDtos;
import com.padle.core.padelcoreservice.dto.RegistroRequestDto;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.mapper.AccountMapper;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.security.oauth2.CustomOAuth2User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {
    private final PlayerRepository players;
    private final PlayerService playerService;
    private final AccountMapper mapper;

    public PlayerPadel player(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated())
            throw new AccountException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED");
        var principal = authentication.getPrincipal();
        if (principal instanceof CustomOAuth2User oauth) principal = oauth.getPlayer();
        if (!(principal instanceof PlayerPadel player))
            throw new AccountException(HttpStatus.FORBIDDEN, "PLAYER_REQUIRED");
        return players.findById(player.getId())
                .filter(PlayerPadel::isActivo)
                .orElseThrow(() -> new AccountException(HttpStatus.UNAUTHORIZED, "SESSION_REQUIRED"));
    }

    public AccountDtos.Profile profile(Authentication authentication) {
        return mapper.profile(player(authentication));
    }

    @Transactional
    public void register(RegistroRequestDto request) {
        request.setNombre(request.getNombre().strip());
        request.setApellido(request.getApellido().strip());
        request.setEmail(request.getEmail().strip());
        request.setTelefono(normalize(request.getTelefono()));
        request.setTelegramUsername(telegram(request.getTelegramUsername()));
        if (!request.passwordsMatch()) invalid("PASSWORD_MISMATCH", "confirmPassword");
        duplicates(request.getEmail(), request.getTelefono(), request.getTelegramUsername(), Optional.empty());
        playerService.registrarJugador(request);
    }

    @Transactional
    public AccountDtos.Profile update(Authentication authentication, AccountDtos.ProfileUpdate input) {
        var player = player(authentication);
        var request = new AccountDtos.ProfileUpdate(input.nombre().strip(), input.apellido().strip(), input.email().strip(),
                normalize(input.telefono()), telegram(input.telegramUsername()), input.nivelJugador(), input.preferredLocale());
        if (!Nivel.isPlayerLevel(request.nivelJugador())) invalid("INVALID_FIELD", "nivelJugador");
        if (request.telefono() == null && request.telegramUsername() == null) invalid("CONTACT_REQUIRED", "telefono", "telegramUsername");
        if (!player.isGuestAccount() && !player.getEmail().equals(request.email())) invalid("EMAIL_IMMUTABLE", "email");
        duplicates(request.email(), request.telefono(), request.telegramUsername(), Optional.of(player));
        mapper.update(request, player);
        return mapper.profile(players.save(player));
    }

    @Transactional
    public void changePassword(Authentication authentication, AccountDtos.PasswordChange request) {
        var player = player(authentication);
        if (!playerService.checkPassword(player, request.currentPassword())) invalid("CURRENT_PASSWORD_INVALID", "currentPassword");
        if (!request.newPassword().equals(request.confirmPassword())) invalid("PASSWORD_MISMATCH", "confirmPassword");
        playerService.cambiarPassword(player.getId(), request.newPassword());
    }

    private void duplicates(String email, String phone, String telegram, Optional<PlayerPadel> current) {
        if (current.map(player -> !player.getEmail().equals(email)).orElse(true) && players.existsByEmail(email))
            invalid("EMAIL_DUPLICATE", "email");
        if (phone != null && current.map(player -> !Objects.equals(player.getTelefono(), phone)).orElse(true)
                && players.existsByTelefono(phone)) invalid("PHONE_DUPLICATE", "telefono");
        if (telegram != null && current.map(player -> !telegram.equalsIgnoreCase(Objects.toString(player.getTelegramUsername(), ""))).orElse(true)
                && players.existsByTelegramUsernameIgnoreCase(telegram)) invalid("TELEGRAM_DUPLICATE", "telegramUsername");
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
