package com.padle.core.padelcoreservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingDto {
    private Long id;
    private Long playerId;
    private String playerNombre;
    private String playerApellido;
    private String playerNombreCompleto;
    private String playerEmail;
    private Integer puntos;
    private Integer torneosJugados;
    private Integer torneosGanados;
    private Integer partidosGanados;
    private Integer partidosPerdidos;
    private Integer setsGanados;
    private Integer setsPerdidos;
    private String nivelActual;
    private Integer posicionAnterior;
    private Integer posicionActual;
    private Double winRate;
    private String tendencia;

    // Estadísticas adicionales
    public Integer getTotalPartidos() {
        return partidosGanados + partidosPerdidos;
    }

    public String getWinRateFormateado() {
        return String.format("%.1f%%", winRate != null ? winRate : 0.0);
    }
}