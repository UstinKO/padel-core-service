package com.padle.core.padelcoreservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class GuestProfileClaimRepository {
    private final JdbcClient jdbc;

    public UUID saveRequest(long accountId, String phone) {
        return jdbc.sql("""
                INSERT INTO guest_profile_claims(id, account_id, phone_key)
                VALUES (:reference, :account, :phone)
                ON CONFLICT (account_id) WHERE approved_at IS NULL DO UPDATE
                SET id = EXCLUDED.id, phone_key = EXCLUDED.phone_key, created_at = CURRENT_TIMESTAMP
                RETURNING id
                """).param("reference", UUID.randomUUID()).param("account", accountId).param("phone", phone)
                .query(UUID.class).single();
    }

    public Optional<Claim> request(UUID reference, boolean lock) {
        var sql = """
                SELECT account_id, phone_key, guest_id, resolved_player_id
                FROM guest_profile_claims
                WHERE id = :reference AND account_id IS NOT NULL
                  AND (approved_at IS NOT NULL OR created_at > CURRENT_TIMESTAMP - INTERVAL '7 days')
                """;
        return jdbc.sql(lock ? sql + " FOR UPDATE" : sql).param("reference", reference)
                .query((rs, n) -> new Claim(rs.getLong("account_id"), rs.getString("phone_key"),
                        rs.getObject("guest_id", Long.class), rs.getObject("resolved_player_id", Long.class))).optional();
    }

    public Optional<Long> resolved(long guestId) {
        return jdbc.sql("""
                SELECT resolved_player_id FROM guest_profile_claims
                WHERE guest_id = :guest AND approved_at IS NOT NULL
                """).param("guest", guestId).query(Long.class).optional();
    }

    public void approve(UUID reference, long guestId, long accountId, long ownerId) {
        jdbc.sql("""
                UPDATE guest_profile_claims SET guest_id = :guest, approved_by = :owner,
                    approved_at = CURRENT_TIMESTAMP, resolved_player_id = :account
                WHERE id = :reference
                """).param("guest", guestId).param("owner", ownerId).param("account", accountId)
                .param("reference", reference).update();
    }

    public void activate(long guestId, String phone, long ownerId) {
        jdbc.sql("""
                INSERT INTO guest_profile_claims(id, guest_id, phone_key, approved_at, approved_by, resolved_player_id)
                VALUES (:reference, :guest, :phone, CURRENT_TIMESTAMP, :owner, :guest)
                """).param("reference", UUID.randomUUID()).param("guest", guestId).param("phone", phone)
                .param("owner", ownerId).update();
    }

    public record Claim(long accountId, String phone, Long guestId, Long resolvedPlayerId) {}
}
