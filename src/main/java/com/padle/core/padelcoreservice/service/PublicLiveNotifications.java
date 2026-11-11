package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.live.PublicLiveDtos.Invalidation;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicLiveNotifications {
    private final TournamentKingOfCourtRepository kings;
    private final TournamentRepository events;
    private final SimpMessagingTemplate messaging;
    private final Object transactionKey = new Object();

    public void kingChanged(long kingId) {
        kings.findById(kingId).ifPresent(king -> changed(king.getTournament().getId()));
    }

    public void changed(long eventId) {
        var supported = events.findById(eventId).filter(event -> event.getTipo() == TournamentType.KING_OF_COURT
                || event.getTipo() == TournamentType.AMERICANO_TEAMS
                || event.getTipo() == TournamentType.AMERICANO && event.getModalidad() == Modalidad.DOBLES);
        if(supported.isEmpty()) return;
        if(!TransactionSynchronizationManager.isSynchronizationActive()) { send(eventId); return; }
        changes().add(eventId);
    }

    private Set<Long> changes() {
        var existing = TransactionSynchronizationManager.getResource(transactionKey);
        if(existing instanceof PendingInvalidations pending) return pending.ids();
        Set<Long> ids = new LinkedHashSet<>();
        TransactionSynchronizationManager.bindResource(transactionKey,new PendingInvalidations(ids));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { ids.forEach(PublicLiveNotifications.this::send); }
            @Override public void afterCompletion(int status) { TransactionSynchronizationManager.unbindResourceIfPossible(transactionKey); }
        });
        return ids;
    }

    private record PendingInvalidations(Set<Long> ids) {}

    private void send(long eventId) {
        try { messaging.convertAndSend("/topic/public/live/"+eventId,new Invalidation(eventId,"CHANGED")); }
        catch(MessagingException exception) {
            log.info("Публичное live-уведомление недоступно: eventId={}, type={}",eventId,exception.getClass().getSimpleName());
        }
    }
}
