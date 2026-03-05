package com.padle.core.padelcoreservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {
    private String type; // "RESULT_SAVED", "ROUND_COMPLETED", "TOURNAMENT_FINISHED", etc.
    private Long tournamentId;
    private Long kingId;
    private Object data;
    private String timestamp;
}