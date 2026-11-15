package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.dto.GuestProfileClaimDtos.ProfileChoice;
import com.padle.core.padelcoreservice.dto.GuestProfileClaimDtos.QueueItem;
import com.padle.core.padelcoreservice.dto.GuestProfileClaimDtos.Status;
import com.padle.core.padelcoreservice.dto.GuestProfileClaimDtos.Verification;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class GuestProfileClaimRepository {
    private static final String COLUMNS = "id, request_number, coalesce(account_id, rejected_account_id) AS account_id, phone_key, " +
            "guest_id, resolved_player_id, status, created_at, review_reason";
    private final JdbcClient jdbc;

    public Claim saveRequest(long accountId, String phone) {
        return jdbc.sql("""
                INSERT INTO guest_profile_claims(id, account_id, phone_key)
                VALUES (:reference, :account, :phone)
                ON CONFLICT (account_id) WHERE approved_at IS NULL DO UPDATE
                SET phone_key = guest_profile_claims.phone_key
                RETURNING
                """ + COLUMNS).param("reference", UUID.randomUUID()).param("account", accountId).param("phone", phone)
                .query((rs, n) -> claim(rs)).single();
    }

    public Optional<Claim> request(UUID reference, boolean lock) {
        String sql = "SELECT " + COLUMNS + " FROM guest_profile_claims WHERE id = :reference AND (account_id IS NOT NULL OR rejected_account_id IS NOT NULL)";
        return jdbc.sql(lock ? sql + " FOR UPDATE" : sql).param("reference", reference)
                .query((rs, n) -> claim(rs)).optional();
    }

    public Optional<Claim> matchingRequest(long accountId, String phone, boolean lock) {
        String sql = "SELECT " + COLUMNS + "\n" + """
                 FROM guest_profile_claims
                 WHERE account_id = :account AND phone_key = :phone AND status <> 'REJECTED'
                 ORDER BY created_at DESC, request_number DESC LIMIT 1
                 """;
        return jdbc.sql(lock ? sql + " FOR UPDATE" : sql).param("account", accountId).param("phone", phone)
                .query((rs, n) -> claim(rs)).optional();
    }

    public List<Claim> forAccount(long accountId) {
        return jdbc.sql("SELECT " + COLUMNS + "\n" + """
                 FROM guest_profile_claims WHERE account_id = :account OR rejected_account_id = :account
                 ORDER BY CASE WHEN status IN ('PENDING','NEEDS_DETAILS') THEN 0 ELSE 1 END, created_at DESC, request_number DESC
                 """).param("account", accountId)
                .query((rs, n) -> claim(rs)).list();
    }

    public List<QueueItem> queue(String search, String normalizedPhone, Status status, int limit, long offset) {
        return jdbc.sql("""
                SELECT c.id, c.request_number, c.created_at, p.id AS account_id, c.phone_key, c.status,
                       concat_ws(' ', p.nombre, p.apellido) AS account_name
                FROM guest_profile_claims c JOIN player_padel_db p ON p.id = coalesce(c.account_id, c.rejected_account_id)
                WHERE (CAST(:status AS text) IS NULL OR c.status = :status)
                  AND (:search = '' OR c.request_number::text = :search OR c.id::text = lower(:search)
                        OR p.id::text = :search OR c.phone_key = :phone
                        OR strpos(lower(concat_ws(' ',p.nombre,p.apellido,p.email)), lower(:search)) > 0)
                ORDER BY CASE c.status WHEN 'PENDING' THEN 0 WHEN 'NEEDS_DETAILS' THEN 1 ELSE 2 END,
                         c.created_at DESC, c.request_number DESC
                LIMIT :limit OFFSET :offset
                """).param("status", status == null ? null : status.name()).param("search", search)
                .param("phone", normalizedPhone).param("limit", limit).param("offset", offset)
                .query((rs, n) -> new QueueItem(rs.getObject("id", UUID.class), rs.getLong("request_number"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getLong("account_id"),
                        rs.getString("account_name"), rs.getString("phone_key"),
                        Status.valueOf(rs.getString("status")))).list();
    }

    public List<ProfileChoice> profileChoices(String search, boolean guest) {
        return jdbc.sql("""
                SELECT id, concat_ws(' ',nombre,apellido) AS name FROM player_padel_db
                WHERE activo AND (lower(email) LIKE '%@1padel.guest') = :guest
                  AND (id::text = :search OR (length(:search) >= 2
                    AND strpos(lower(concat_ws(' ', nombre, apellido, email)), lower(:search)) > 0))
                ORDER BY CASE WHEN id::text = :search THEN 0 ELSE 1 END, nombre, apellido, id
                LIMIT 12
                """).param("search", search).param("guest", guest).query(ProfileChoice.class).list();
    }

    public long pendingCount() {
        return jdbc.sql("SELECT count(*) FROM guest_profile_claims WHERE status = 'PENDING' AND account_id IS NOT NULL")
                .query(Long.class).single();
    }

    public Optional<Long> resolved(long guestId) {
        return jdbc.sql("""
                SELECT resolved_player_id FROM guest_profile_claims
                WHERE guest_id = :guest AND approved_at IS NOT NULL
                """).param("guest", guestId).query(Long.class).optional();
    }

    public void decide(UUID reference, Status status, long ownerId, String reason) {
        jdbc.sql("""
                UPDATE guest_profile_claims
                SET status = :status, reviewed_by = :owner, reviewed_at = CURRENT_TIMESTAMP, review_reason = :reason,
                    rejected_account_id = CASE WHEN :status = 'REJECTED' THEN account_id ELSE rejected_account_id END,
                    account_id = CASE WHEN :status = 'REJECTED' THEN NULL ELSE account_id END
                WHERE id = :reference
                """).param("reference", reference).param("status", status.name()).param("owner", ownerId)
                .param("reason", reason).update();
    }

    public void approve(UUID reference, long guestId, long accountId, long ownerId, Verification method, boolean independentReview) {
        jdbc.sql("""
                UPDATE guest_profile_claims SET guest_id = :guest, approved_by = :owner,
                    approved_at = CURRENT_TIMESTAMP, resolved_player_id = :account, status = 'MERGED',
                    reviewed_by = :owner, reviewed_at = CURRENT_TIMESTAMP, verification_method = :method,
                    review_reason = NULL, initiator = CASE WHEN :manual THEN 'ADMIN' ELSE initiator END
                WHERE id = :reference
                """).param("guest", guestId).param("owner", ownerId).param("account", accountId)
                .param("reference", reference).param("method", method.name()).param("manual", independentReview).update();
    }

    public void recordManual(long guestId, Long accountId, long resolvedId, String phone, long ownerId, Verification method) {
        jdbc.sql("""
                INSERT INTO guest_profile_claims(id, guest_id, account_id, phone_key, approved_at, approved_by,
                    resolved_player_id, status, initiator, verification_method, reviewed_by, reviewed_at)
                VALUES (:id, :guest, :account, :phone, CURRENT_TIMESTAMP, :owner, :resolved,
                    'MERGED', 'ADMIN', :method, :owner, CURRENT_TIMESTAMP)
                """).param("id", UUID.randomUUID()).param("guest", guestId).param("account", accountId)
                .param("phone", phone).param("owner", ownerId).param("resolved", resolvedId)
                .param("method", method.name()).update();
    }

    private static Claim claim(ResultSet rs) throws SQLException {
        return new Claim(rs.getObject("id", UUID.class), rs.getLong("request_number"), rs.getLong("account_id"),
                rs.getString("phone_key"), rs.getObject("guest_id", Long.class),
                rs.getObject("resolved_player_id", Long.class), Status.valueOf(rs.getString("status")),
                rs.getObject("created_at", OffsetDateTime.class), rs.getString("review_reason"));
    }

    public record Claim(UUID reference, long number, long accountId, String phone, Long guestId,
                        Long resolvedPlayerId, Status status, OffsetDateTime createdAt, String reviewReason) {}
}
