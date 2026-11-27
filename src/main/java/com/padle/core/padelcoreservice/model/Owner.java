package com.padle.core.padelcoreservice.model;

import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Действующий владелец в Spring Security и MVC. Имя класса и поля входят в формат сессии. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Owner implements UserDetails {

    @Serial
    private static final long serialVersionUID = 8872565096194256998L;

    private Long id;

    private String email;

    private String password;

    private String firstName;

    private String lastName;

    private String phone;

    private String organizationName;

    @Builder.Default
    private OwnerRole role = OwnerRole.ORGANIZER;

    private Long clubId;

    @Builder.Default
    private Set<Long> clubIds = new HashSet<>();

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastLoginAt;

    @Builder.Default
    private Boolean isActive = true;

    // Реализация UserDetails для Spring Security
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Возвращаем роль пользователя
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return isActive;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive;
    }

    // Helper методы
    public String getFullName() {
        return firstName + " " + lastName;
    }

    public void updateLastLogin() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public boolean isSuperAdmin() {
        return role == OwnerRole.SUPER_ADMIN;
    }

    public boolean isAdminRole() {
        return role == OwnerRole.ADMIN;
    }

    public boolean isClubAdmin() {
        return role == OwnerRole.CLUB_ADMIN;
    }

    public boolean canViewAllTournaments() {
        return role == OwnerRole.SUPER_ADMIN || role == OwnerRole.ADMIN;
    }
}
