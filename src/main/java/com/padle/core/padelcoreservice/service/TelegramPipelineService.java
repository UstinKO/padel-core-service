package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.TelegramPipelineState;
import com.padle.core.padelcoreservice.repository.TelegramPipelineStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kill-switch для конвейера /develop-feature, запускаемого через Telegram (scripts/telegram-relay/).
 * Единственная строка в БД (id=1) — relay опрашивает {@link com.padle.core.padelcoreservice.controller.api.TelegramPipelineStatusController}
 * перед каждым fire и пропускает сообщение, если paused=true.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TelegramPipelineService {

    private static final long STATE_ID = 1L;

    private final TelegramPipelineStateRepository repository;

    public boolean isPaused() {
        return getOrCreateState().isPaused();
    }

    @Transactional
    public boolean togglePaused(String updatedBy) {
        TelegramPipelineState state = getOrCreateState();
        state.setPaused(!state.isPaused());
        state.setUpdatedBy(updatedBy);
        repository.save(state);
        log.info("Telegram-конвейер: paused={} (изменил {})", state.isPaused(), updatedBy);
        return state.isPaused();
    }

    private TelegramPipelineState getOrCreateState() {
        return repository.findById(STATE_ID)
                .orElseGet(() -> repository.save(
                        TelegramPipelineState.builder().id(STATE_ID).paused(false).build()));
    }
}
