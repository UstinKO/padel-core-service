package com.padle.core.padelcoreservice.model;

import com.padle.core.padelcoreservice.model.enums.Nivel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "player_padel_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "passwordHash")
public class PlayerPadel implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "apellido", nullable = false, length = 100)
    private String apellido;

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "telefono", length = 20)
    private String telefono;

    /**
     * Ник игрока в Telegram.
     * Необязателен при регистрации на сайт.
     * Обязателен при регистрации на турнир если telefono не указан.
     */
    @Column(name = "telegram_username", length = 64)
    private String telegramUsername;

    /** Заполняется когда игрок пишет боту /start. Нужен для личных DM-напоминаний. */
    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime fechaRegistro;

    @Column(name = "fecha_actualizacion")
    @UpdateTimestamp
    private LocalDateTime fechaActualizacion;

    @Column(name = "email_confirmado", nullable = false)
    private boolean emailConfirmado = false;

    @Column(name = "fecha_confirmacion_email")
    private LocalDateTime fechaConfirmacionEmail;

    @Column(name = "codigo_confirmacion", length = 50)
    private String codigoConfirmacion;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @Column(name = "oauth2_user", nullable = false)
    private boolean oauth2User = false;

    @Column(name = "provider", length = 50)
    private String provider;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    // issue #82: NOT NULL с DEFAULT 'SIN_ESPECIFICAR' (миграция v1.44) — ddl-auto=none,
    // так что nullable=false тут не меняет схему сам по себе, только документирует её
    // реальное состояние в БД.
    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_jugador", length = 20, nullable = false)
    private Nivel nivelJugador;

    @Column(name = "preferred_locale", length = 5)
    private String preferredLocale = "es";

    // ========== Реализация UserDetails ==========

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLAYER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return activo;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return activo && emailConfirmado;
    }

    // ========== Вспомогательные методы ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerPadel player = (PlayerPadel) o;
        return Objects.equals(id, player.id) &&
                Objects.equals(email, player.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }

    public String getNombreCompleto() {
        return nombre + " " + apellido;
    }

    public void confirmarEmail() {
        this.emailConfirmado = true;
        this.fechaConfirmacionEmail = LocalDateTime.now();
        this.codigoConfirmacion = null;
    }

    /**
     * Проверяет достаточно ли контактных данных для регистрации на турнир.
     * Нужен любой телефон ИЛИ Telegram ник.
     */
    public boolean hasValidContact() {
        boolean hasPhone = telefono != null && !telefono.isBlank();
        boolean hasTelegram = telegramUsername != null && !telegramUsername.isBlank();
        return hasPhone || hasTelegram;
    }

    private static final String GUEST_EMAIL_SUFFIX = "@1padel.guest";

    /**
     * Гостевой аккаунт — создан администратором за игрока, который сам ещё не
     * регистрировался (см. TestTournamentController.resolveOrCreateGuestPlayer).
     * У таких аккаунтов временный email вида guest.<timestamp>@1padel.guest —
     * единственный признак, по которому в /perfil и в админке разрешается смена
     * email/выдача временного пароля (issue #217).
     */
    public static boolean isGuestEmail(String email) {
        return email != null && email.endsWith(GUEST_EMAIL_SUFFIX);
    }

    public boolean isGuestAccount() {
        return isGuestEmail(email);
    }

    public Locale getLocale() {
        String lang = preferredLocale != null ? preferredLocale : "es";
        return Set.of("es", "ru", "en").contains(lang)
                ? Locale.forLanguageTag(lang)
                : Locale.forLanguageTag("es");
    }
}