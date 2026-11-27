package com.padle.core.padelcoreservice.security;

import com.padle.core.persistence.identity.PlayerPadel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static com.padle.core.padelcoreservice.support.PlayerFixtures.playerAuthentication;
import static com.padle.core.padelcoreservice.support.PlayerFixtures.principal;
import static org.assertj.core.api.Assertions.assertThat;

class PlayerSessionAuthenticationTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void replacingPrincipalDoesNotCreateSessionForRequestAuthentication() {
        var player = PlayerPadel.builder().id(1L).email("player@example.test").build();
        var authentication = playerAuthentication(player);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var request = new MockHttpServletRequest();

        new PlayerSessionAuthentication().replace(authentication, principal(player), request, new MockHttpServletResponse());

        assertThat(request.getSession(false)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(authentication);
    }
}
