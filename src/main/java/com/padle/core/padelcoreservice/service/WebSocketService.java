package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.KingOfCourtStateDTO;
import com.padle.core.padelcoreservice.dto.MatchHistoryDTO;
import com.padle.core.padelcoreservice.dto.WebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public void notifyResultSaved(Long kingId, Long roundId, Integer courtNumber, MatchHistoryDTO result) {
        WebSocketMessage message = WebSocketMessage.builder()
                .type("RESULT_SAVED")
                .kingId(kingId)
                .data(result)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();

        messagingTemplate.convertAndSend("/topic/tournament/" + kingId, message);
        log.info("WebSocket notification sent for result saved: kingId={}, court={}", kingId, courtNumber);
    }

    public void notifyRoundCompleted(Long kingId, Integer roundNumber) {
        WebSocketMessage message = WebSocketMessage.builder()
                .type("ROUND_COMPLETED")
                .kingId(kingId)
                .data(roundNumber)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();

        messagingTemplate.convertAndSend("/topic/tournament/" + kingId, message);
        log.info("WebSocket notification sent for round completed: kingId={}, round={}", kingId, roundNumber);
    }

    public void notifyNextRoundStarted(Long kingId, Integer newRoundNumber) {
        WebSocketMessage message = WebSocketMessage.builder()
                .type("NEXT_ROUND_STARTED")
                .kingId(kingId)
                .data(newRoundNumber)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();

        messagingTemplate.convertAndSend("/topic/tournament/" + kingId, message);
        log.info("WebSocket notification sent for next round: kingId={}, newRound={}", kingId, newRoundNumber);
    }

    public void notifyTournamentFinished(Long kingId, KingOfCourtStateDTO finalResults) {
        WebSocketMessage message = WebSocketMessage.builder()
                .type("TOURNAMENT_FINISHED")
                .kingId(kingId)
                .data(finalResults)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();

        messagingTemplate.convertAndSend("/topic/tournament/" + kingId, message);
        log.info("WebSocket notification sent for tournament finished: kingId={}", kingId);
    }

    public void notifyTournamentStateUpdated(Long kingId, KingOfCourtStateDTO state) {
        WebSocketMessage message = WebSocketMessage.builder()
                .type("STATE_UPDATED")
                .kingId(kingId)
                .data(state)
                .timestamp(LocalDateTime.now().format(FORMATTER))
                .build();

        messagingTemplate.convertAndSend("/topic/tournament/" + kingId, message);
        log.info("WebSocket notification sent for state update: kingId={}", kingId);
    }
}