package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlayerTelegramLinks {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PlayerTelegramProperties properties;
    private final PlayerTelegramApi api;
    private final PlayerTelegramStore store;

    public LinkStatus status(PlayerPadel player) {
        return new LinkStatus(available(), store.connection(player.getId()).filter(Connection::enabled).isPresent(),
                store.preference(player.getId()));
    }

    public LinkStatus preference(PlayerPadel player, ContactPreference preference) {
        if (!properties.enabled() || !player.isActivo() || player.isGuestAccount()) {
            throw new AccountException(HttpStatus.CONFLICT, "TELEGRAM_UNAVAILABLE");
        }
        store.preference(player.getId(), preference);
        return status(player);
    }

    public boolean available() {
        return properties.enabled() && api.available()
                && properties.botUsername().matches("[A-Za-z0-9_]{5,32}");
    }

    public Link issue(PlayerPadel player, Locale locale) {
        if (!available() || !player.isActivo() || player.isGuestAccount()) {
            throw new AccountException(HttpStatus.CONFLICT, "TELEGRAM_UNAVAILABLE");
        }
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var expiry = OffsetDateTime.now().plusMinutes(10);
        String language = Set.of("es", "ru", "en").contains(locale.getLanguage()) ? locale.getLanguage() : "es";
        store.token(player.getId(), hash(token), expiry, language);
        return new Link("https://t.me/" + properties.botUsername() + "?start=" + token, expiry);
    }

    public Optional<Long> bind(String token, long userId, long chatId) {
        if (userId <= 0 || chatId != userId || !token.matches("[A-Za-z0-9_-]{32}")) {
            return Optional.empty();
        }
        return store.bind(hash(token), userId, chatId);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 недоступен", exception);
        }
    }
}
