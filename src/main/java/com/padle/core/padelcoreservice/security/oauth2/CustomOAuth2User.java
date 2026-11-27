package com.padle.core.padelcoreservice.security.oauth2;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.io.Serial;
import java.io.Serializable;

/** Формат OAuth2 principal, записанный в сессиях предыдущими версиями приложения. */
public final class CustomOAuth2User implements Serializable {

    @Serial
    private static final long serialVersionUID = 7424620199077772615L;

    private final OAuth2User oAuth2User;
    private final PlayerPadel player;

    private CustomOAuth2User(OAuth2User oAuth2User, PlayerPadel player) {
        this.oAuth2User = oAuth2User;
        this.player = player;
    }

    @Serial
    private Object readResolve() {
        return new OAuth2PlayerPrincipal(oAuth2User, player.toPrincipal());
    }
}
