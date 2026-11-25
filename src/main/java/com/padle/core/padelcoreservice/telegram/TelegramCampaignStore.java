package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.telegram.CampaignModels.*;
import com.padle.core.padelcoreservice.telegram.TelegramAudienceModels.*;
import com.padle.core.padelcoreservice.exception.AccountException;
import org.springframework.http.HttpStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class TelegramCampaignStore {
    private final JdbcTemplate jdbc;

    public Optional<Header> find(UUID id) {
        return jdbc.query("SELECT * FROM telegram_campaigns WHERE id=?", this::header, id).stream().findFirst();
    }
    public Header lock(UUID id) {
        return jdbc.query("SELECT * FROM telegram_campaigns WHERE id=? FOR UPDATE", this::header, id)
                .stream().findFirst().orElseThrow(() -> new AccountException(
                        HttpStatus.NOT_FOUND,"TELEGRAM_CAMPAIGN_NOT_FOUND"));
    }
    public Optional<Header> byRequest(long owner, UUID request) {
        return jdbc.query("SELECT * FROM telegram_campaigns WHERE owner_id=? AND request_id=?", this::header, owner, request)
                .stream().findFirst();
    }
    private Header header(ResultSet rs, int index) throws SQLException {
        return new Header(rs.getObject("id", UUID.class), rs.getLong("owner_id"), rs.getObject("tournament_id", Long.class),
                rs.getString("author"), rs.getString("message_text"), rs.getString("kind"), rs.getString("state"),
                rs.getString("fingerprint"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("expires_at", OffsetDateTime.class));
    }
    public Creation create(long ownerId, String author, Long event, UUID request, String hash, String text, String kind, UUID source) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO telegram_campaigns(id,owner_id,author,tournament_id,request_id,fingerprint,message_text,kind,state,source_id)
                VALUES(?,?,?,?,?,?,?,?, 'DRAFT', ?) ON CONFLICT(owner_id,request_id) DO NOTHING
                """, id, ownerId, author, event, request, hash, text, kind, source);
        return new Creation(byRequest(ownerId, request).orElseThrow(),inserted == 1);
    }
    public void add(UUID campaign, Candidate candidate, Optional<Link> link) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO telegram_campaign_recipients(campaign_id,player_id,player_name,category,groups_text,
                      chat_id,connection_at,state,reason) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
                    """);
            statement.setObject(1,campaign); statement.setLong(2,candidate.playerId());
            statement.setString(3,candidate.name()); statement.setString(4,candidate.category().name());
            statement.setString(5,candidate.groups().stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
            if (link.isEmpty()) {
                statement.setNull(6,Types.BIGINT); statement.setNull(7,Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                var value = link.orElseThrow(); statement.setLong(6,value.chatId()); statement.setObject(7,value.connectedAt());
            }
            statement.setString(8,candidate.availability()==Availability.AVAILABLE && link.isPresent() ? "PENDING" : "SKIPPED");
            statement.setString(9,candidate.availability()==Availability.AVAILABLE && link.isEmpty() ? "CONNECTION_CHANGED" : candidate.availability().name());
            return statement;
        });
    }

    public Optional<Link> connection(long player) {
        return jdbc.query("SELECT player_id,chat_id,linked_at FROM player_telegram_connections WHERE player_id=? AND enabled",
                (rs,n) -> new Link(rs.getLong(1),rs.getLong(2),rs.getObject(3,OffsetDateTime.class)), player).stream().findFirst();
    }
    public List<StoredRecipient> rows(UUID id) {
        return jdbc.query("""
                SELECT r.*, CASE WHEN r.state='IN_FLIGHT' AND r.updated_at<CURRENT_TIMESTAMP-INTERVAL '2 minutes'
                  THEN COALESCE((SELECT CASE WHEN d.state='IN_FLIGHT' THEN 'UNKNOWN' ELSE d.state END
                    FROM player_telegram_deliveries d WHERE d.dedupe_key='campaign:'||r.campaign_id||':'||r.player_id||':'||r.attempt),
                    'UNKNOWN') ELSE r.state END AS visible_state
                FROM telegram_campaign_recipients r WHERE campaign_id=? ORDER BY player_name,player_id
                """, (rs,n) ->
                new StoredRecipient(rs.getLong("player_id"), rs.getString("player_name"), rs.getString("category"),
                        rs.getString("groups_text"), rs.getObject("chat_id",Long.class), rs.getObject("connection_at",OffsetDateTime.class),
                        rs.getString("visible_state"),rs.getString("reason"),rs.getInt("attempt")), id);
    }
    public List<Header> history(Long event, int page) {
        return jdbc.query("SELECT * FROM telegram_campaigns " + (event == null ? "" : "WHERE tournament_id=? ")
                + "ORDER BY created_at DESC,id LIMIT 20 OFFSET ?", this::header,
                event == null ? new Object[]{page * 20} : new Object[]{event,page * 20});
    }
    public void queue(UUID id) {
        jdbc.update("UPDATE telegram_campaigns SET state='QUEUED',confirmed_at=CURRENT_TIMESTAMP WHERE id=? AND state='DRAFT'",id);
        finishCampaigns();
    }
    public boolean action(UUID id, UUID request) {
        return jdbc.update("INSERT INTO telegram_campaign_actions(campaign_id,request_id) VALUES(?,?) ON CONFLICT DO NOTHING",id,request)==1;
    }
    public void retry(UUID id) {
        jdbc.update("UPDATE telegram_campaign_recipients SET state='PENDING',updated_at=CURRENT_TIMESTAMP WHERE campaign_id=? AND state='FAILED'",id);
        jdbc.update("UPDATE telegram_campaigns SET state='QUEUED' WHERE id=?",id);
    }
    public void result(UUID id, long player, String state, String reason) {
        jdbc.update("UPDATE telegram_campaign_recipients SET state=?,reason=?,updated_at=CURRENT_TIMESTAMP WHERE campaign_id=? AND player_id=?",
                state,reason,id,player);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Dispatch> claim() {
        return jdbc.query("""
                WITH candidate AS (
                  SELECT r.campaign_id,r.player_id FROM telegram_campaign_recipients r
                  JOIN telegram_campaigns c ON c.id=r.campaign_id
                  WHERE r.state='PENDING' AND c.state='QUEUED' ORDER BY c.created_at,r.player_id
                  FOR UPDATE OF r SKIP LOCKED LIMIT 1
                ) UPDATE telegram_campaign_recipients r SET state='IN_FLIGHT',attempt=attempt+1,updated_at=CURRENT_TIMESTAMP
                  FROM candidate c WHERE r.campaign_id=c.campaign_id AND r.player_id=c.player_id
                  RETURNING r.campaign_id,r.player_id,r.attempt
                """, (rs,n)->new Dispatch(rs.getObject(1,UUID.class),rs.getLong(2),rs.getInt(3))).stream().findFirst();
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void uncertain(Dispatch dispatch) {
        jdbc.update("""
                UPDATE telegram_campaign_recipients SET state='UNKNOWN',reason='UNCERTAIN',updated_at=CURRENT_TIMESTAMP
                WHERE campaign_id=? AND player_id=? AND state='IN_FLIGHT' AND attempt=?
                """,dispatch.campaignId(),dispatch.playerId(),dispatch.attempt());
    }
    @Transactional
    public void recover() {
        jdbc.update("""
                UPDATE telegram_campaign_recipients r SET state=COALESCE((SELECT CASE WHEN d.state='IN_FLIGHT' THEN 'UNKNOWN'
                  ELSE d.state END FROM player_telegram_deliveries d WHERE d.dedupe_key=
                  'campaign:'||r.campaign_id||':'||r.player_id||':'||r.attempt),'UNKNOWN'),reason='RECOVERED'
                WHERE r.state='IN_FLIGHT' AND r.updated_at<CURRENT_TIMESTAMP-INTERVAL '2 minutes'
                """);
        finishCampaigns();
    }
    public void finishCampaigns() {
        jdbc.update("""
                UPDATE telegram_campaigns c SET state='FINISHED' WHERE c.state='QUEUED' AND NOT EXISTS
                (SELECT 1 FROM telegram_campaign_recipients r WHERE r.campaign_id=c.id AND r.state IN ('PENDING','IN_FLIGHT'))
                """);
    }
}
