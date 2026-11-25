package com.padle.core.padelcoreservice.dto.americano;

import lombok.Data;
import java.util.List;

// ── Конфигурация запуска Team Americano ─────────────────────────────────────

@Data
public class TeamAmericanoConfigDto {
    private Integer courts;           // Количество кортов
    private Integer pointsPerMatch;   // Очков за матч (напр. 32)
    private com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode scoringMode =
            com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode.POINTS;
}
