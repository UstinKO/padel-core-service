package com.padle.core.padelcoreservice.model;

import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.model.enums.TournamentVisibility;
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

    @Column(name = "owner_id")
    private Long ownerId;

    // LFPT-437: момент отправки email-напоминания за 5ч до старта парного турнира.
    // NULL — ещё не отправлено; не-NULL — отправлено (или обработано без CONFIRMED-игроков),
    // повторная отправка не выполняется даже после редактирования турнира.
    @Column(name = "pair_reminder_sent_at")
    private LocalDateTime pairReminderSentAt;

    // LFPT-443: момент отправки email-напоминания за 5ч до старта индивидуального турнира
    // (AMERICANO, KING_OF_COURT) либо Cancha Abierta. Общее поле для обоих писем —
    // на турнир приходится ровно один tipo, коллизий нет. Та же семантика, что у pairReminderSentAt.
    @Column(name = "start_reminder_sent_at")
    private LocalDateTime startReminderSentAt;

    // LFPT-483: показывать ли уровень игрока (PlayerPadel.nivelJugador) рядом с каждым
    // участником в публичном списке зарегистрированных — обратимый эксперимент на уровне
    // конкретного турнира, по умолчанию выключено. UI-чекбокс — LFPT-484.
    @Column(name = "mostrar_nivel", nullable = false)
    @Builder.Default
    private Boolean mostrarNivel = false;

    // LFPT-491: видимость турнира — PUBLICO (попадает в публичные списки/расписание) или
    // SOLO_POR_ENLACE (закрытое мероприятие: не в списках/навигации, доступно только по
    // прямой ссылке /torneo/{id}). Enum, а не boolean — задел под будущий третий режим
    // «по приглашению» без новой миграции.
    @Enumerated(EnumType.STRING)
    @Column(name = "visibilidad", nullable = false, length = 30)
    @Builder.Default
    private TournamentVisibility visibilidad = TournamentVisibility.PUBLICO;

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

    public LocalDateTime calculateDefaultDeadline() {
        LocalDateTime startDateTime = LocalDateTime.of(fechaInicio, horaInicio);
        return startDateTime.minusHours(24);
    }

    public boolean canCancelRegistration() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveDeadline = deadlineCancelacion != null
                ? deadlineCancelacion
                : calculateDefaultDeadline();
        return now.isBefore(effectiveDeadline);
    }

    public String getFormattedPrecio() {
        return moneda + " " + precio;
    }

    // LFPT-374: nombre del torneo ya no se ingresa manualmente — se genera a partir
    // de los campos ya obligatorios generoFormato + categoriaNivel (ej. "Masculino · C6").
    public static String generateNombre(GenderFormat generoFormato, Nivel categoriaNivel) {
        String genero = generoFormato != null ? generoFormato.getValue() : null;
        String nivel = categoriaNivel != null ? categoriaNivel.getDisplay() : null;

        if (genero == null && nivel == null) {
            return "Torneo";
        }
        if (genero == null) {
            return nivel;
        }
        if (nivel == null) {
            return genero;
        }
        return genero + " · " + nivel;
    }
}