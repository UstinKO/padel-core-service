package com.padle.core.padelcoreservice.security.oauth2;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

public class CustomOAuth2User implements OAuth2User, UserDetails {

    private final OAuth2User oAuth2User;
    @Getter
    private final PlayerPadel player;

    public CustomOAuth2User(OAuth2User oAuth2User, PlayerPadel player) {
        this.oAuth2User = oAuth2User;
        this.player = player;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return oAuth2User.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_PLAYER"));
    }

    @Override
    public String getName() {
        return player.getEmail();
    }

    public String getEmail() {
        return player.getEmail();
    }

    public String getNombre() {
        return player.getNombre();
    }

    public String getApellido() {
        return player.getApellido();
    }

    // ===== Методы UserDetails =====

    @Override
    public String getPassword() {
        return player.getPassword();
    }

    @Override
    public String getUsername() {
        return player.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return player.isActivo();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return player.isActivo() && player.isEmailConfirmado();
    }
}