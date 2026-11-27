package com.padle.core.padelcoreservice.security;

import com.padle.core.application.identity.PlayerIdentity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.List;

@Getter
@AllArgsConstructor
public class PlayerPrincipal implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_PLAYER"));

    private final PlayerIdentity identity;
    private final String nombre;
    private final String apellido;
    private final String password;
    private final boolean activo;
    private final boolean emailConfirmado;
    private final boolean validContact;
    private final String preferredLocale;

    protected PlayerPrincipal(PlayerPrincipal principal) {
        this(principal.identity, principal.nombre, principal.apellido, principal.password, principal.activo,
                principal.emailConfirmado, principal.validContact, principal.preferredLocale);
    }

    public Long getId() {
        return identity.id();
    }

    public String getEmail() {
        return identity.email();
    }

    public String getNombreCompleto() {
        return nombre + " " + apellido;
    }

    @Override
    public String getUsername() {
        return getEmail();
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public boolean isAccountNonLocked() {
        return activo;
    }

    @Override
    public boolean isEnabled() {
        return activo && emailConfirmado;
    }
}
