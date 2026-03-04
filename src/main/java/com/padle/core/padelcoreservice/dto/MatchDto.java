package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.model.enums.MatchStatus;
import com.padle.core.padelcoreservice.model.enums.MatchType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchDto {
    private Long id;
    private Long tournamentId;
    private String tournamentNombre;
    private Integer ronda;
    private Integer partidoNumero;
    private MatchType tipo;

    // Jugadores
    private Long player1Id;
    private String player1Nombre;
    private String player1Apellido;
    private String player1NombreCompleto;

    private Long player2Id;
    private String player2Nombre;
    private String player2Apellido;
    private String player2NombreCompleto;

    private Long player3Id;
    private String player3Nombre;
    private String player3Apellido;
    private String player3NombreCompleto;

    private Long player4Id;
    private String player4Nombre;
    private String player4Apellido;
    private String player4NombreCompleto;

    private String equipo1Nombre;
    private String equipo2Nombre;

    // Resultados
    private Integer set1Equipo1;
    private Integer set1Equipo2;
    private Integer set2Equipo1;
    private Integer set2Equipo2;
    private Integer set3Equipo1;
    private Integer set3Equipo2;
    private Integer superSetEquipo1;
    private Integer superSetEquipo2;

    private Long ganadorId;
    private String ganadorNombre;
    private Long perdedorId;
    private String perdedorNombre;

    private Integer puntosEquipo1;
    private Integer puntosEquipo2;

    private MatchStatus estado;
    private LocalDateTime fechaProgramada;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String cancha;
    private String arbitro;
    private String observaciones;

    private String resultadoString;
    private Integer totalSetsJugados;
    private Boolean isFinalizado;
    private Boolean isEnCurso;
}