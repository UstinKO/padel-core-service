package com.padle.core.padelcoreservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "ranking_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ranking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "player_id", nullable = false, unique = true)
    private PlayerPadel player;

    @Column(name = "puntos", nullable = false)
    private Integer puntos;

    @Column(name = "torneos_jugados", nullable = false)
    private Integer torneosJugados;

    @Column(name = "torneos_ganados", nullable = false)
    private Integer torneosGanados;

    @Column(name = "partidos_ganados", nullable = false)
    private Integer partidosGanados;

    @Column(name = "partidos_perdidos", nullable = false)
    private Integer partidosPerdidos;

    @Column(name = "sets_ganados", nullable = false)
    private Integer setsGanados;

    @Column(name = "sets_perdidos", nullable = false)
    private Integer setsPerdidos;

    @Column(name = "nivel_actual", length = 10)
    private String nivelActual;

    @Column(name = "posicion_actual")
    private Integer posicionActual;

    @Column(name = "posicion_anterior")
    private Integer posicionAnterior;

    @Column(name = "mejor_posicion")
    private Integer mejorPosicion;

    @Column(name = "rachas_actual")
    private Integer rachasActual;

    @Column(name = "rachas_maxima")
    private Integer rachasMaxima;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;

    // Вспомогательные методы
    public Double getWinRate() {
        if (partidosGanados + partidosPerdidos == 0) {
            return 0.0;
        }
        return (double) partidosGanados / (partidosGanados + partidosPerdidos) * 100;
    }

    public String getTendencia() {
        if (posicionActual == null || posicionAnterior == null) {
            return "NUEVO";
        }
        if (posicionActual < posicionAnterior) {
            return "ASCENDENTE";
        } else if (posicionActual > posicionAnterior) {
            return "DESCENDENTE";
        } else {
            return "ESTABLE";
        }
    }

    public void calcularMejorPosicion() {
        if (mejorPosicion == null || posicionActual < mejorPosicion) {
            setMejorPosicion(posicionActual);
        }
    }

    @PrePersist
    @PreUpdate
    public void preUpdate() {
        lastCalculatedAt = LocalDateTime.now();
        calcularMejorPosicion();
    }
}