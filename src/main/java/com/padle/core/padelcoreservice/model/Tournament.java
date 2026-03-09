package com.padle.core.padelcoreservice.model;

import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static com.padle.core.padelcoreservice.model.enums.Nivel.*;

@Entity
@Table(name = "tournaments_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"infoDetallada", "registrations"})
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "duracion", length = 50)
    private String duracion;

    @Column(name = "genero_formato", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private GenderFormat generoFormato;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria_nivel")
    private Nivel categoriaNivel;

    @Column(name = "tipo", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TournamentType tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad", nullable = false, length = 20)
    private Modalidad modalidad;

    @Column(name = "cupo_max", nullable = false)
    private Integer cupoMax;

    @Column(name = "precio", nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(name = "moneda", nullable = false, length = 10)
    @Builder.Default
    private String moneda = "ARS";

    @Column(name = "estado", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TournamentStatus estado = TournamentStatus.REGISTRO_ABIERTO;

    @Column(name = "deadline_cancelacion")
    private LocalDateTime deadlineCancelacion;

    @Column(name = "info_detallada", columnDefinition = "TEXT")
    private String infoDetallada;

    @Column(name = "contacto_organizador", nullable = false, length = 100)
    private String contactoOrganizador;

    @Column(name = "faq_url", length = 500)
    private String faqUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Связь с регистрациями
    @OneToMany(mappedBy = "tournament", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<TournamentRegistration> registrations = new ArrayList<>();

    // Вспомогательные методы
    public LocalDateTime getFechaHoraInicioCompleta() {
        return LocalDateTime.of(fechaInicio, horaInicio);
    }

    public boolean isRegistrationOpen() {
        return estado == TournamentStatus.REGISTRO_ABIERTO;
    }

    public boolean canCancelRegistration() {
        if (deadlineCancelacion == null) {
            return true;
        }
        return LocalDateTime.now().isBefore(deadlineCancelacion);
    }

    public String getFormattedPrecio() {
        return moneda + " " + precio;
    }
}