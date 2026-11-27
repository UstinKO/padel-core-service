package com.padle.core.padelcoreservice.receipts;

import com.padle.core.persistence.tournament.TournamentRegistration;
import com.padle.core.padelcoreservice.receipts.ReceiptModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ReceiptStore {
    private final JdbcClient jdbc;
    private static final String METADATA = """
            SELECT c.id, c.registration_id, c.registration_version, c.player_id,
              concat_ws(' ',p.nombre,p.apellido) player_name, t.nombre event_name, c.content_type,
              c.state, c.created_at, c.reviewed_at, c.rejection_reason, c.payment_id,
              (r.cancellation_version = c.registration_version AND r.is_active AND
                r.status IN ('CONFIRMED','PAIR_REGISTERED')) current_cycle
            FROM payment_receipts c JOIN tournament_registrations_db r ON r.id = c.registration_id
            JOIN tournaments_db t ON t.id = r.tournament_id JOIN player_padel_db p ON p.id = c.player_id
            """;

    public Upload pending(long update, long player, long chat, String fileId) {
        jdbc.sql("DELETE FROM telegram_receipt_uploads WHERE expires_at < CURRENT_TIMESTAMP AND receipt_id IS NULL").update();
        jdbc.sql("""
                INSERT INTO telegram_receipt_uploads(id, source_update, player_id, chat_id, file_id)
                VALUES (:id,:update,:player,:chat,:file) ON CONFLICT (source_update) DO NOTHING
                """).param("id", UUID.randomUUID()).param("update", update).param("player", player)
                .param("chat", chat).param("file", fileId).update();
        return jdbc.sql("SELECT *, expires_at < CURRENT_TIMESTAMP expired FROM telegram_receipt_uploads WHERE source_update=:update AND player_id=:player")
                .param("update", update).param("player", player).query(ReceiptStore::upload).single();
    }

    public Upload upload(UUID id, long player, long chat, boolean lock) {
        return jdbc.sql("SELECT *, expires_at < CURRENT_TIMESTAMP expired FROM telegram_receipt_uploads "
                        + "WHERE id=:id AND player_id=:player AND chat_id=:chat" + (lock ? " FOR UPDATE" : ""))
                .param("id", id).param("player", player).param("chat", chat).query(ReceiptStore::upload).optional()
                .orElseThrow(ReceiptRegistrations::unavailable);
    }

    public UUID save(Upload upload, TournamentRegistration row, File file) {
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO payment_receipts(id,registration_id,registration_version,player_id,content_type,content,sha256)
                VALUES (:id,:registration,:version,:player,:type,:content,:hash)
                ON CONFLICT (registration_id,registration_version,sha256) DO NOTHING
                """).param("id", id).param("registration", row.getId()).param("version", row.getCancellationVersion())
                .param("player", row.getPlayer().getId()).param("type", file.contentType())
                .param("content", file.content(), Types.BINARY).param("hash", ReceiptFiles.hash(file.content())).update();
        var saved = jdbc.sql("SELECT id FROM payment_receipts WHERE registration_id=:registration AND registration_version=:version AND sha256=:hash")
                .param("registration", row.getId()).param("version", row.getCancellationVersion())
                .param("hash", ReceiptFiles.hash(file.content())).query(UUID.class).single();
        jdbc.sql("UPDATE telegram_receipt_uploads SET receipt_id=:receipt WHERE id=:id")
                .param("receipt", saved).param("id", upload.id()).update();
        return saved;
    }

    public List<Receipt> list(long tournamentId) {
        return jdbc.sql(METADATA + " WHERE t.id=:tournament ORDER BY c.created_at DESC,c.id")
                .param("tournament", tournamentId).query(ReceiptStore::receipt).list();
    }

    public Receipt require(long tournamentId, UUID id, boolean lock) {
        return jdbc.sql(METADATA + " WHERE t.id=:tournament AND c.id=:id" + (lock ? " FOR UPDATE OF c" : ""))
                .param("tournament", tournamentId).param("id", id).query(ReceiptStore::receipt).optional()
                .orElseThrow(ReceiptRegistrations::unavailable);
    }

    public Optional<File> file(UUID id) {
        return jdbc.sql("SELECT content_type,content FROM payment_receipts WHERE id=:id").param("id", id)
                .query((r,n) -> new File(r.getString("content_type"), r.getBytes("content"))).optional();
    }

    public void review(UUID id, State state, long owner, Long paymentId, String reason) {
        jdbc.sql("""
                UPDATE payment_receipts SET state=:state,reviewed_at=CURRENT_TIMESTAMP,reviewed_by=:owner,
                  payment_id=:payment,rejection_reason=:reason WHERE id=:id AND state='PENDING'
                """).param("id", id).param("state", state.name()).param("owner", owner)
                .param("payment", paymentId, Types.BIGINT).param("reason", reason, Types.VARCHAR).update();
    }

    private static Upload upload(ResultSet r, int n) throws SQLException {
        return new Upload(r.getObject("id", UUID.class), r.getLong("player_id"), r.getLong("chat_id"),
                r.getString("file_id"), r.getObject("receipt_id", UUID.class), r.getBoolean("expired"));
    }

    private static Receipt receipt(ResultSet r, int n) throws SQLException {
        return new Receipt(r.getObject("id", UUID.class), r.getLong("registration_id"), r.getLong("registration_version"),
                r.getLong("player_id"), r.getString("player_name"), r.getString("event_name"), r.getString("content_type"),
                State.valueOf(r.getString("state")), r.getObject("created_at", OffsetDateTime.class),
                r.getObject("reviewed_at", OffsetDateTime.class), r.getString("rejection_reason"),
                r.getObject("payment_id", Long.class), r.getBoolean("current_cycle"));
    }
}
