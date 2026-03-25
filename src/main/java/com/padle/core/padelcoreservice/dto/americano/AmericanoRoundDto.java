package com.padle.core.padelcoreservice.dto.americano;

import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmericanoRoundDto {
    private Long id;
    private Long tournamentId;
    private Integer roundNumber;
    private AmericanoRoundStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer pointsPerMatch;
    private Boolean isDoubles;
    private Integer courts;
    private String note;
    private Integer totalMatches;
    private Integer completedMatches;
    private List<AmericanoMatchDto> matches;

    /**
     * Список имён игроков, отдыхающих в этом раунде (bye).
     * Заполняется в AmericanoService при построении DTO.
     * Пример: ["Ana García", "Pedro López"]
     */
    private List<String> byePlayerNames;
}