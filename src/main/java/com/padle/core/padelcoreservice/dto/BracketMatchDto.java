package com.padle.core.padelcoreservice.dto;

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
public class BracketMatchDto {
    private Long id;
    private Long tournamentId;
    private String tournamentNombre;
    private Integer ronda;
    private Integer partidoNumero;
    private String tipo;

    // Jugadores/Equipos
    private Long player1Id;
    private String player1Nombre;
    private String player1Apellido;
    private String player1NombreCompleto;
    private String player1Email;

    private Long player2Id;
    private String player2Nombre;
    private String player2Apellido;
    private String player2NombreCompleto;
    private String player2Email;

    // Para parejas
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

    private String estado;
    private LocalDateTime fechaProgramada;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String cancha;

    // IDs de los siguientes partidos
    private Long siguientePartidoId;
    private Integer siguientePartidoRonda;
    private Integer siguientePartidoNumero;
    private String siguientePartidoPosicion; // "ganador" o "perdedor" (para el bracket)

    // Metadata
    private Boolean isFinalizado;
    private Boolean isEnCurso;
    private Boolean isProgramado;
    private String resultadoString;

    // Para saber si el jugador actual está en este partido
    private Boolean isJugadorParticipa;
}