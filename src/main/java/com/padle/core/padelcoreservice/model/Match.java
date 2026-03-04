package com.padle.core.padelcoreservice.model;

import com.padle.core.padelcoreservice.model.enums.MatchStatus;
import com.padle.core.padelcoreservice.model.enums.MatchType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "matches_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Column(name = "ronda", nullable = false)
    private Integer ronda; // 1: primera ronda, 2: cuartos, 3: semifinal, 4: final, etc.

    @Column(name = "partido_numero", nullable = false)
    private Integer partidoNumero; // Número de partido en la ronda

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private MatchType tipo;

    // Jugadores/Equipos
    @Column(name = "player1_id")
    private Long player1Id;

    @Column(name = "player2_id")
    private Long player2Id;

    @Column(name = "player3_id") // Para partidos de parejas
    private Long player3Id;

    @Column(name = "player4_id") // Para partidos de parejas
    private Long player4Id;

    @Column(name = "equipo1_nombre", length = 255)
    private String equipo1Nombre; // Para parejas sin nombre, concatenar nombres

    @Column(name = "equipo2_nombre", length = 255)
    private String equipo2Nombre; // Para parejas sin nombre, concatenar nombres

    // Resultados
    @Column(name = "set1_equipo1")
    private Integer set1Equipo1;

    @Column(name = "set1_equipo2")
    private Integer set1Equipo2;

    @Column(name = "set2_equipo1")
    private Integer set2Equipo1;

    @Column(name = "set2_equipo2")
    private Integer set2Equipo2;

    @Column(name = "set3_equipo1")
    private Integer set3Equipo1;

    @Column(name = "set3_equipo2")
    private Integer set3Equipo2;

    @Column(name = "super_set_equipo1")
    private Integer superSetEquipo1; // Para super tie-break

    @Column(name = "super_set_equipo2")
    private Integer superSetEquipo2; // Para super tie-break

    @Column(name = "ganador_id")
    private Long ganadorId; // ID del jugador/equipo ganador

    @Column(name = "perdedor_id")
    private Long perdedorId; // ID del jugador/equipo perdedor

    @Column(name = "puntos_equipo1")
    private Integer puntosEquipo1; // Puntos totales del partido

    @Column(name = "puntos_equipo2")
    private Integer puntosEquipo2; // Puntos totales del partido

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @Builder.Default
    private MatchStatus estado = MatchStatus.PROGRAMADO;

    @Column(name = "fecha_programada")
    private LocalDateTime fechaProgramada;

    @Column(name = "fecha_inicio")
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "cancha", length = 50)
    private String cancha;

    @Column(name = "arbitro", length = 255)
    private String arbitro;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Métodos helper
    public boolean isPartidoIndividual() {
        return tipo == MatchType.INDIVIDUAL;
    }

    public boolean isPartidoParejas() {
        return tipo == MatchType.PAREJAS;
    }

    public boolean isFinalizado() {
        return estado == MatchStatus.FINALIZADO;
    }

    public boolean isEnCurso() {
        return estado == MatchStatus.EN_CURSO;
    }

    public String getResultadoString() {
        StringBuilder resultado = new StringBuilder();
        if (set1Equipo1 != null && set1Equipo2 != null) {
            resultado.append(set1Equipo1).append("-").append(set1Equipo2);
        }
        if (set2Equipo1 != null && set2Equipo2 != null) {
            resultado.append(", ").append(set2Equipo1).append("-").append(set2Equipo2);
        }
        if (set3Equipo1 != null && set3Equipo2 != null) {
            resultado.append(", ").append(set3Equipo1).append("-").append(set3Equipo2);
        }
        if (superSetEquipo1 != null && superSetEquipo2 != null) {
            resultado.append(", super: ").append(superSetEquipo1).append("-").append(superSetEquipo2);
        }
        return resultado.toString();
    }

    public String getGanadorNombre() {
        if (ganadorId == null) return "Por definir";
        if (isPartidoIndividual()) {
            return ganadorId.equals(player1Id) ? "Jugador 1" : "Jugador 2";
        } else {
            // Para parejas, habría que buscar los nombres
            return "Equipo " + ganadorId;
        }
    }

    public Integer getTotalSetsJugados() {
        int total = 0;
        if (set1Equipo1 != null) total++;
        if (set2Equipo1 != null) total++;
        if (set3Equipo1 != null) total++;
        if (superSetEquipo1 != null) total++;
        return total;
    }
}