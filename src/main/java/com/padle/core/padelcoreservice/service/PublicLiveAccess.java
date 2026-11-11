package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.exception.PublicLiveException;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicLiveAccess {
    private final TournamentRepository events;
    public Tournament readable(long eventId) {
        if(eventId < 1) throw new PublicLiveException("LIVE_INVALID_ID");
        return events.findById(eventId).filter(event -> Boolean.TRUE.equals(event.getIsActive())
                && event.getEstado() != TournamentStatus.BORRADOR)
                .orElseThrow(() -> new PublicLiveException("LIVE_EVENT_NOT_FOUND"));
    }
}
