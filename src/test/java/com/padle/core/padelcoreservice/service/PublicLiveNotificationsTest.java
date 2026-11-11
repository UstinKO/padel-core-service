package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.live.PublicLiveDtos.Invalidation;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Optional;
import static org.mockito.Mockito.*;

class PublicLiveNotificationsTest {
    private final TournamentRepository events=mock(TournamentRepository.class);
    private final SimpMessagingTemplate broker=mock(SimpMessagingTemplate.class);
    private final PublicLiveNotifications notifications=new PublicLiveNotifications(mock(TournamentKingOfCourtRepository.class),events,broker);
    private final TransactionTemplate transaction=new TransactionTemplate(new TestTransactionManager());

    @Test void oneSafeInvalidationAfterCommitEvenWithSeveralMutationPaths() {
        when(events.findById(42L)).thenReturn(Optional.of(Tournament.builder().tipo(TournamentType.KING_OF_COURT).build()));
        transaction.executeWithoutResult(status -> {
            notifications.changed(42); notifications.changed(42); verifyNoInteractions(broker);
        });
        verify(broker).convertAndSend("/topic/public/live/42",new Invalidation(42,"CHANGED"));
        verifyNoMoreInteractions(broker);
        transaction.executeWithoutResult(status -> notifications.changed(42));
        verify(broker,times(2)).convertAndSend("/topic/public/live/42",new Invalidation(42,"CHANGED"));
    }

    @Test void rollbackAndIndividualAmericanoDoNotPublish() {
        when(events.findById(42L)).thenReturn(Optional.of(Tournament.builder().tipo(TournamentType.KING_OF_COURT).build()));
        transaction.executeWithoutResult(status -> { notifications.changed(42); status.setRollbackOnly(); });
        when(events.findById(43L)).thenReturn(Optional.of(Tournament.builder().tipo(TournamentType.AMERICANO).build()));
        transaction.executeWithoutResult(status -> notifications.changed(43));
        verifyNoInteractions(broker);
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) {}
        @Override protected void doRollback(DefaultTransactionStatus status) {}
    }
}
