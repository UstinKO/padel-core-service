package com.padle.core.padelcoreservice.service.conduct;

import com.padle.core.padelcoreservice.service.KingOfCourtService;
import com.padle.core.padelcoreservice.service.WebSocketService;
import com.padle.core.padelcoreservice.service.conduct.AdditionalPointsService.KingPointsChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdditionalPointsBroadcast {

    private final KingOfCourtService king;
    private final WebSocketService websocket;

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void refresh(KingPointsChanged change) {
        try {
            websocket.notifyTournamentStateUpdated(change.kingId(), king.getCurrentState(change.kingId()));
        } catch (RuntimeException ex) {
            log.warn("Не удалось обновить табло турнира {} после изменения дополнительных очков", change.kingId(), ex);
        }
    }
}
