package com.padle.core.padelcoreservice.marketing;

import com.padle.core.application.registration.RegistrationAttributionSnapshots;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttributionRegistrationJournal {
    private final RegistrationAttributionSnapshots snapshots;
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
        snapshots.record(id, actor, visitor, at);
    }
}
