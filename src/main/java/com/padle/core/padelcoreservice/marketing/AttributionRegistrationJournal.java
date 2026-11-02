package com.padle.core.padelcoreservice.marketing;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttributionRegistrationJournal {
    private final JdbcTemplate jdbc;
    private final InternalAttributionService context;
    private final ObjectProvider<PlatformTransactionManager> transactions;

    public void registration(Long registrationId) {
        Instant recordedAt = Instant.now();
        Optional<HttpServletRequest> request = currentRequest().filter(MarketingPolicy::hasConsent)
                .filter(value -> "POST".equals(value.getMethod()))
                .filter(value -> value.getRequestURI().matches(
                        "/players/tournaments/[0-9]+/register|/api/tournaments/double/.*"));
        Optional<Long> actor = request.flatMap(value -> InternalAttributionService.playerId());
        Optional<UUID> visitor = request.filter(value -> actor.isPresent())
                .flatMap(value -> MarketingPolicy.cookie(value, InternalAttributionService.COOKIE))
                .filter(value -> value.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
                .map(UUID::fromString);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                save(registrationId, actor, visitor, recordedAt);
            }
        });
    }

    private Optional<HttpServletRequest> currentRequest() {
        return Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .filter(ServletRequestAttributes.class::isInstance).map(ServletRequestAttributes.class::cast)
                .map(ServletRequestAttributes::getRequest);
    }

    private void save(Long id, Optional<Long> actor, Optional<UUID> visitor, Instant at) {
        var transaction = new TransactionTemplate(transactions.getObject());
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            transaction.executeWithoutResult(status -> snapshot(id, actor.orElse(-1L), visitor.orElse(new UUID(0, 0)), at));
        } catch (DataAccessException | TransactionException error) {
            log.error("Не удалось сохранить внутренний источник регистрации {}", id, error);
        }
    }

    private void snapshot(Long id, Long actor, UUID visitor, Instant at) {
        context.bind(visitor, actor);
        jdbc.update("""
                UPDATE marketing_registrations_db mr SET
                    attribution_visitor_id = v.id,
                    acquisition_player_id = a.player_id,
                    acquisition_touch = CASE WHEN r.player_id = ? THEN a.first_touch END,
                    registration_touch = CASE WHEN (v.last_touch::jsonb->>'capturedAt')::timestamptz
                        BETWEEN ?::timestamptz - INTERVAL '90 days' AND ?::timestamptz THEN v.last_touch END,
                    attribution_recorded_at = ?,
                    attribution_expires_at = COALESCE(a.expires_at, v.expires_at)
                FROM tournament_registrations_db r
                LEFT JOIN marketing_attribution_visitors_db v ON v.id = ? AND v.consent
                    AND v.expires_at >= CURRENT_TIMESTAMP AND v.bound_player_id = ?
                LEFT JOIN marketing_acquisition_db a ON a.player_id = r.player_id
                    AND a.player_id = ? AND a.expires_at >= CURRENT_TIMESTAMP
                WHERE mr.registration_id = r.id AND r.id = ? AND mr.attribution_recorded_at IS NULL
                """, actor, Timestamp.from(at), Timestamp.from(at), Timestamp.from(at), visitor, actor, actor, id);
    }
}
