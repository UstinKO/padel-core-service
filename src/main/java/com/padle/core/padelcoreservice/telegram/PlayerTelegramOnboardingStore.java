package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PlayerTelegramOnboardingStore {
    private final JdbcTemplate jdbc;
    private final PlayerTelegramStore connections;
    private final PlayerTelegramAccess access;

    record Request(UUID id, long userId, long chatId, OffsetDateTime expiresAt,
                   Long playerId, String locale, boolean completed) {}

    @Transactional
    public void issue(UUID id, long userId, String hash, OffsetDateTime expiry, String locale) {
        connections.guardConnections();
        jdbc.update("DELETE FROM player_telegram_onboarding WHERE expires_at <= CURRENT_TIMESTAMP OR user_id = ?", userId);
        jdbc.update("""
                INSERT INTO player_telegram_onboarding(id,user_id,chat_id,token_hash,expires_at,locale)
                VALUES (?,?,?,?,?,?)
                """, id, userId, userId, hash, expiry, locale);
    }

    public Optional<Request> review(String hash) {
        return jdbc.query("SELECT * FROM player_telegram_onboarding WHERE token_hash=? AND expires_at>CURRENT_TIMESTAMP",
                (r,n) -> new Request(r.getObject("id", UUID.class), r.getLong("user_id"), r.getLong("chat_id"),
                        r.getObject("expires_at", OffsetDateTime.class), r.getObject("player_id", Long.class),
                        r.getString("locale"), r.getBoolean("completed")), hash).stream().findFirst();
    }

    @Transactional
    public Request stage(String hash, long playerId, String locale) {
        connections.guardConnections();
        var request = review(hash).orElseThrow(PlayerTelegramOnboardingStore::invalid);
        if (request.playerId() != null && request.playerId() != playerId) throw invalid();
        requirePlayer(playerId);
        requireNoConflict(playerId, request.userId());
        if (request.completed()) return request;
        jdbc.update("UPDATE player_telegram_onboarding SET player_id=?,locale=? WHERE id=?", playerId, locale, request.id());
        return review(hash).orElseThrow(PlayerTelegramOnboardingStore::invalid);
    }

    @Transactional
    public Optional<Long> complete(UUID id, long userId) {
        connections.guardConnections();
        var player = jdbc.queryForList("""
                SELECT player_id FROM player_telegram_onboarding
                WHERE id=? AND user_id=? AND expires_at>CURRENT_TIMESTAMP AND player_id IS NOT NULL
                """, Long.class, id, userId).stream().findFirst();
        return player.map(playerId -> {
            requirePlayer(playerId);
            requireNoConflict(playerId, userId);
            boolean completed = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT completed FROM player_telegram_onboarding WHERE id=?", Boolean.class, id));
            if (completed) return connections.byUser(userId).filter(c -> c.playerId()==playerId)
                    .map(c -> playerId).orElseThrow(PlayerTelegramOnboardingStore::invalid);
            connections.saveConnection(playerId, userId, userId);
            jdbc.update("UPDATE player_padel_db SET preferred_locale=(SELECT locale FROM player_telegram_onboarding WHERE id=?) WHERE id=?",
                    id, playerId);
            jdbc.update("UPDATE player_telegram_onboarding SET completed=true WHERE id=?", id);
            jdbc.update("DELETE FROM player_telegram_tokens WHERE player_id=?", playerId);
            return playerId;
        });
    }

    @Transactional
    public void revoke(long userId) {
        connections.guardConnections();
        jdbc.update("DELETE FROM player_telegram_onboarding WHERE user_id=?", userId);
    }

    private void requirePlayer(long playerId) {
        if (!access.allowsPlayer(playerId) || jdbc.queryForList(
                "SELECT email FROM player_padel_db WHERE id=? AND activo FOR SHARE", String.class, playerId)
                .stream().filter(email -> !PlayerPadel.isGuestEmail(email)).findFirst().isEmpty()) throw invalid();
    }

    private void requireNoConflict(long playerId, long userId) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM player_telegram_connections
                WHERE (player_id=? AND user_id<>?) OR (user_id=? AND player_id<>?))
                """, Boolean.class, playerId, userId, userId, playerId))) {
            throw new AccountException(HttpStatus.CONFLICT, "TELEGRAM_LINK_CONFLICT");
        }
    }

    static AccountException invalid() {
        return new AccountException(HttpStatus.CONFLICT, "TELEGRAM_LINK_INVALID");
    }
}
