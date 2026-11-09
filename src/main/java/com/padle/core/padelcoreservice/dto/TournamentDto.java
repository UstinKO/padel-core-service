package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.model.enums.TournamentVisibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentDto {
    private Long id;
    private Long clubId;
    private Long ownerId;
    private String clubNombre;
    private String clubDireccion;
    private String nombre;
    // Форматы HTML5 date/time/datetime-local — иначе th:field выводит значение
    // в формате локали ("12/1/26", "10:00 AM"), и браузер молча блокирует сабмит формы
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaInicio;
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime horaInicio;
    private String duracion;
    @Positive(message = "{calendar.validation.duration}")
    private Integer durationMinutes;
    @Size(max = 100, message = "{calendar.validation.zone}")
    private String timeZone;

    private GenderFormat generoFormato;
    private String categoriaNivel;
    private TournamentType tipo;
    private Modalidad modalidad;
    private Integer cupoMax;
    private BigDecimal precio;
    private String moneda;
    private TournamentStatus estado;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime deadlineCancelacion;
    // Дедлайн отмены, который реально проверяет Tournament.canCancelRegistration()
    // (индивидуальный, если задан, иначе "старт - 24ч"), уже отформатированный для показа игроку.
    private String cancellationDeadlineFormatted;
    private String infoDetallada;
    private String contactoOrganizador;
    @jakarta.validation.constraints.Size(max = 2000)
    private String paymentInstructions;
    private String faqUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Boolean isActive;
    private Boolean mostrarNivel;
    private TournamentVisibility visibilidad;

    @JsonIgnore
    @AssertTrue(message = "{calendar.validation.zone}")
    public boolean isCalendarTimeZoneValid() {
        return timeZone == null || timeZone.isBlank() || ZoneId.getAvailableZoneIds().contains(timeZone);
    }

    // Поля для статистики
    private Integer inscritosActuales;
    private Integer disponibles;
    private Integer waitlistCount;

    // Вычисляемые поля
    public boolean isRegistrationOpen() {
        return estado == TournamentStatus.REGISTRO_ABIERTO;
    }

    public boolean isFull() {
        return inscritosActuales != null && cupoMax != null && inscritosActuales >= cupoMax;
    }

    public boolean hasAvailableSpots() {
        return !isFull();
    }

    public int getDisponibles() {
        if (inscritosActuales == null || cupoMax == null) return 0;
        return Math.max(0, cupoMax - inscritosActuales);
    }
}