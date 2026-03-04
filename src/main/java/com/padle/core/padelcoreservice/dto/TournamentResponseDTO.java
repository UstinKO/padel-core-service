package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
public class TournamentResponseDTO {
    private Long id;
    private Long clubId;
    private String clubNombre;
    private String nombre;
    private LocalDate fechaInicio;
    private LocalTime horaInicio;
    private String duracion;
    private GenderFormat generoFormato;
    private String categoriaNivel;
    private TournamentType tipo;
    private Integer cupoMax;
    private Integer inscritosActuales;
    private Integer disponibles;
    private Integer waitlistCount;
    private BigDecimal precio;
    private String moneda;
    private TournamentStatus estado;
    private LocalDateTime deadlineCancelacion;
    private String infoDetallada;
    private String contactoOrganizador;
    private String faqUrl;
    private LocalDateTime createdAt;
    private Boolean isActive;

    // Вычисляемые поля
    public boolean isRegistrationOpen() {
        return estado == TournamentStatus.REGISTRO_ABIERTO;
    }

    public boolean isFull() {
        return inscritosActuales != null && cupoMax != null && inscritosActuales >= cupoMax;
    }

    public int getDisponibles() {
        if (inscritosActuales == null || cupoMax == null) return 0;
        return Math.max(0, cupoMax - inscritosActuales);
    }
}