package com.padle.core.padelcoreservice.security.oauth2;

import com.padle.core.padelcoreservice.security.PlayerPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serial;
import java.util.Map;

public final class OAuth2PlayerPrincipal extends PlayerPrincipal implements OAuth2User {

    @Serial
    private static final long serialVersionUID = 1L;

    private final OAuth2User delegate;

    public OAuth2PlayerPrincipal(OAuth2User delegate, PlayerPrincipal player) {
        super(player);
        this.delegate = delegate;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public String getName() {
        return getEmail();
    }

    public OAuth2PlayerPrincipal withPlayer(PlayerPrincipal player) {
        return new OAuth2PlayerPrincipal(delegate, player);
    }
}
