package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PlayerTelegramStore {
    private final JdbcTemplate jdbc;
    private final PlayerTelegramAccess access;

    public ContactPreference preference(long playerId) {
        return jdbc.query("SELECT preference FROM player_contact_preferences WHERE player_id = ?",
                (r, n) -> ContactPreference.valueOf(r.getString("preference")), playerId)
                .stream().findFirst().orElse(ContactPreference.UNSPECIFIED);
    }

    public Map<Long, ContactPreference> preferences(List<Long> playerIds) {
        if (playerIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", Collections.nCopies(playerIds.size(), "?"));
        return jdbc.query("SELECT player_id, preference FROM player_contact_preferences WHERE player_id IN ("
                        + placeholders + ")", (r, n) -> Map.entry(r.getLong("player_id"),
                        ContactPreference.valueOf(r.getString("preference"))), playerIds.toArray())
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Transactional
    public void preference(long playerId, ContactPreference preference) {
        lockConnections();
        savePreference(playerId, preference);
        if (preference == ContactPreference.WHATSAPP) {
            removeConnection(playerId);
        }
    }

    private void savePreference(long playerId, ContactPreference preference) {
        jdbc.update("""
                INSERT INTO player_contact_preferences(player_id, preference) VALUES (?, ?)
                ON CONFLICT (player_id) DO UPDATE SET preference = EXCLUDED.preference
                """, playerId, preference.name());
    }

    private void lockConnections() {
        jdbc.execute("SELECT pg_advisory_xact_lock(1294001)");
    }

    public Optional<Connection> connection(long playerId) {
        return jdbc.query("SELECT * FROM player_telegram_connections WHERE player_id = ?",
                (r, n) -> new Connection(r.getLong("player_id"), r.getLong("user_id"),
                        r.getLong("chat_id"), r.getBoolean("enabled")), playerId).stream().findFirst();
    }

    public Optional<Connection> byUser(long userId) {
        return jdbc.query("SELECT * FROM player_telegram_connections WHERE user_id = ? AND enabled",
                (r, n) -> new Connection(r.getLong("player_id"), r.getLong("user_id"),
                        r.getLong("chat_id"), true), userId).stream().findFirst();
    }

    @Transactional
    public void token(long playerId, String hash, OffsetDateTime expiresAt, String locale) {
        lockConnections();
        savePreference(playerId, ContactPreference.TELEGRAM);
        jdbc.update("UPDATE player_padel_db SET preferred_locale = ? WHERE id = ?", locale, playerId);
        jdbc.update("""
                INSERT INTO player_telegram_tokens(player_id, token_hash, expires_at) VALUES (?, ?, ?)
                ON CONFLICT (player_id) DO UPDATE SET token_hash = EXCLUDED.token_hash,
                    expires_at = EXCLUDED.expires_at
                """, playerId, hash, expiresAt);
    }

    @Transactional
    public Optional<Long> bind(String hash, long userId, long chatId) {
        lockConnections();
        var player = jdbc.queryForList("""
                SELECT t.player_id FROM player_telegram_tokens t JOIN player_padel_db p ON p.id = t.player_id
                WHERE t.token_hash = ? AND t.expires_at > CURRENT_TIMESTAMP AND p.activo = true
                FOR UPDATE OF t
                """, Long.class, hash).stream().findFirst();
        return player.filter(access::allowsPlayer).filter(id -> jdbc.queryForObject("""
                SELECT COUNT(*) FROM player_telegram_connections WHERE user_id = ? AND player_id <> ?
                """, Long.class, userId, id) == 0).map(id -> {
            jdbc.update("""
                    INSERT INTO player_telegram_connections(player_id, user_id, chat_id, enabled)
                    VALUES (?, ?, ?, true) ON CONFLICT (player_id) DO UPDATE
                    SET user_id = EXCLUDED.user_id, chat_id = EXCLUDED.chat_id, enabled = true,
                        linked_at = CURRENT_TIMESTAMP
                    """, id, userId, chatId);
            jdbc.update("DELETE FROM player_telegram_tokens WHERE player_id = ?", id);
            savePreference(id, ContactPreference.TELEGRAM);
            return id;
        });
    }

    @Transactional
    public void disconnect(long playerId) {
        lockConnections();
        removeConnection(playerId);
    }

    private void removeConnection(long playerId) {
        jdbc.update("DELETE FROM player_telegram_connections WHERE player_id = ?", playerId);
        jdbc.update("DELETE FROM player_telegram_tokens WHERE player_id = ?", playerId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String key, Long tournamentId, Long registrationId, long chatId) {
        return jdbc.update("""
                INSERT INTO player_telegram_deliveries(id, dedupe_key, tournament_id, registration_id, chat_id, state)
                VALUES (?, ?, ?, ?, ?, 'IN_FLIGHT') ON CONFLICT (dedupe_key) DO NOTHING
                """, UUID.randomUUID(), key, tournamentId, registrationId, chatId) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(String key, Result result) {
        jdbc.update("""
                UPDATE player_telegram_deliveries SET state = ?, message_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE dedupe_key = ?
                """, result.state(), result.messageId(), key);
    }

    public List<Delivery> deliveries(long tournamentId) {
        return jdbc.query("""
                SELECT id, dedupe_key, registration_id,
                  CASE WHEN state = 'IN_FLIGHT' AND updated_at < CURRENT_TIMESTAMP - INTERVAL '2 minutes'
                    THEN 'UNKNOWN' ELSE state END AS state
                FROM player_telegram_deliveries WHERE tournament_id = ? ORDER BY created_at DESC
                """, (r, n) -> new Delivery(r.getObject("id", UUID.class), r.getString("dedupe_key"), r.getLong("registration_id"),
                        r.getString("state")), tournamentId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean acquirePoll(UUID lease) {
        return jdbc.update("""
                UPDATE player_telegram_poll SET lease_id = ?, lease_until = CURRENT_TIMESTAMP + INTERVAL '90 seconds'
                WHERE id = 1 AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP)
                """, lease) == 1;
    }

    public long offset() {
        return jdbc.queryForObject("SELECT next_offset FROM player_telegram_poll WHERE id = 1", Long.class);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean advance(UUID lease, long offset) {
        return jdbc.update("""
                UPDATE player_telegram_poll SET next_offset = GREATEST(next_offset, ?),
                    lease_until = CURRENT_TIMESTAMP + INTERVAL '90 seconds'
                WHERE id = 1 AND lease_id = ? AND lease_until > CURRENT_TIMESTAMP
                """, offset, lease) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID lease) {
        jdbc.update("UPDATE player_telegram_poll SET lease_until = NULL, lease_id = NULL WHERE id = 1 AND lease_id = ?", lease);
    }
}
