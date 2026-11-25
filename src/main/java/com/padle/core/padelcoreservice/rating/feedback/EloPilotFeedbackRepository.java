package com.padle.core.padelcoreservice.rating.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class EloPilotFeedbackRepository {
    public enum Source { DASHBOARD, RANKING }
    public enum Topic { QUESTION, IDEA, ERROR, OTHER }
    public enum MailStatus { PENDING, SENT, FAILED }

    public record Entry(long id, long playerId, String authorName, Source source, Topic topic,
                        String message, LocalDateTime createdAt, MailStatus mailStatus) { }

    private final JdbcClient jdbc;

    public Optional<Long> insert(long playerId, String author, UUID requestKey, Source source,
                                 Topic topic, String message) {
        return jdbc.sql("""
                INSERT INTO elo_pilot_feedback_db (player_id, author_name, request_key, source, topic, message)
                VALUES (:playerId, :author, :requestKey, :source, :topic, :message)
                ON CONFLICT (player_id, request_key) DO NOTHING
                RETURNING id
                """)
                .param("playerId", playerId).param("author", author).param("requestKey", requestKey)
                .param("source", source.name()).param("topic", topic.name()).param("message", message)
                .query(Long.class).optional();
    }

    public void markMail(long id, MailStatus status) {
        jdbc.sql("UPDATE elo_pilot_feedback_db SET mail_status = :status WHERE id = :id")
                .param("status", status.name()).param("id", id).update();
    }

    public List<Entry> latest(Long before) {
        return jdbc.sql("""
                SELECT id, player_id, author_name, source, topic, message,
                       created_at AT TIME ZONE 'America/Argentina/Buenos_Aires' AS created_at, mail_status
                FROM elo_pilot_feedback_db
                WHERE id < :before
                ORDER BY id DESC
                LIMIT 50
                """)
                .param("before", before == null ? Long.MAX_VALUE : before)
                .query(Entry.class).list();
    }
}
