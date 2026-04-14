package com.padle.core.padelcoreservice.dto.americano;

import lombok.Data;
import java.util.List;

// ── Конфигурация запуска Team Americano ─────────────────────────────────────

@Data
public class TeamAmericanoConfigDto {
    private Integer courts;           // Количество кортов
    private Integer pointsPerMatch;   // Очков за матч (напр. 32)
}