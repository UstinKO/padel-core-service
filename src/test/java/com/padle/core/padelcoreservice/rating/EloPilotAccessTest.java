package com.padle.core.padelcoreservice.rating;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.security.oauth2.CustomOAuth2User;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EloPilotAccessTest {
    private final EloPilotAccess access = new EloPilotAccess(new EloPilotProperties(true, Set.of(1L), null));

    static UsernamePasswordAuthenticationToken player(long id) {
        var player = new PlayerPadel();
        player.setId(id);
        return new UsernamePasswordAuthenticationToken(player, null, player.getAuthorities());
    }

    @Test
    void allowsOnlySelectedAuthenticatedPlayersAndTheirConfirmedAliases() {
        assertThat(access.allows(player(1), Map.of(8L, 1L))).isTrue();
        assertThat(access.allows(player(8), Map.of(8L, 1L))).isTrue();
        assertThat(access.allows(player(8), Map.of())).isFalse();
        assertThat(access.allows(player(2), Map.of(8L, 1L))).isFalse();
    }

    @Test
    void allowsGoogleLoginUsingVerifiedPlayerIdentityRatherThanGoogleAttributes() {
        var google = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_PLAYER")),
                Map.of("sub", "google", "playerId", 1L), "sub");
        var selected = new CustomOAuth2User(google, (PlayerPadel) player(1).getPrincipal());
        var other = new CustomOAuth2User(google, (PlayerPadel) player(2).getPrincipal());
        assertThat(access.allows(new UsernamePasswordAuthenticationToken(selected, null, selected.getAuthorities()), Map.of())).isTrue();
        assertThat(access.allows(new UsernamePasswordAuthenticationToken(other, null, other.getAuthorities()), Map.of())).isFalse();
    }

    @Test
    void rejectsAnonymousUnauthenticatedOwnerAndUnknownPrincipals() {
        assertThat(access.allows(null, Map.of())).isFalse();
        assertThat(access.allows(new UsernamePasswordAuthenticationToken(player(1).getPrincipal(), null), Map.of())).isFalse();
        assertThat(access.allows(new UsernamePasswordAuthenticationToken(player(1).getPrincipal(), null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER"))), Map.of())).isFalse();
        assertThat(access.allows(new UsernamePasswordAuthenticationToken("1", null,
                List.of(new SimpleGrantedAuthority("ROLE_PLAYER"))), Map.of())).isFalse();
        var transientPlayer = new PlayerPadel();
        assertThat(access.allows(new UsernamePasswordAuthenticationToken(transientPlayer, null,
                transientPlayer.getAuthorities()), Map.of())).isFalse();
    }

    @Test
    void disabledAndEmptyPilotsDoNotGrantAccess() {
        assertThat(new EloPilotAccess(new EloPilotProperties(false, Set.of(1L), null))
                .allows(player(1), Map.of())).isFalse();
        assertThat(new EloPilotAccess(new EloPilotProperties(true, Set.of(), null))
                .allows(player(1), Map.of())).isFalse();
        assertThatThrownBy(() -> new EloPilotProperties(true, Set.of(0L), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
