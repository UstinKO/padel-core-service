package com.padle.core.padelcoreservice.support;

import com.padle.core.padelcoreservice.mapper.PlayerPrincipalMapper;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.security.PlayerPrincipal;
import com.padle.core.padelcoreservice.security.oauth2.OAuth2PlayerPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

public final class PlayerFixtures {

    private PlayerFixtures() {}

    public static PlayerPrincipal principal(PlayerPadel player) {
        return PlayerPrincipalMapper.INSTANCE.toPrincipal(player);
    }

    public static OAuth2PlayerPrincipal oauthPrincipal(OAuth2User provider, PlayerPadel player) {
        return new OAuth2PlayerPrincipal(provider, principal(player));
    }

    public static RequestPostProcessor playerUser(PlayerPadel player) {
        return user(principal(player));
    }

    public static UsernamePasswordAuthenticationToken playerAuthentication(PlayerPadel player) {
        PlayerPrincipal principal = principal(player);
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }
}
