package com.padle.core.padelcoreservice.rating;

import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.padelcoreservice.security.PlayerPrincipal;
import com.padle.core.padelcoreservice.security.oauth2.OAuth2PlayerPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.padle.core.padelcoreservice.support.PlayerFixtures.playerAuthentication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EloPilotAccessTest {
    private final EloPilotAccess access = new EloPilotAccess(new EloPilotProperties(true, Set.of(1L), null));

    static UsernamePasswordAuthenticationToken player(long id) {
        var player = new PlayerPadel();
        player.setId(id);
        return playerAuthentication(player);
    }

    @Test
    void allowsOnlySelectedAuthenticatedPlayersAndTheirConfirmedAliases() {
        assertThat(access.allows(player(1), Map.of(8L, 1L))).isTrue();
        assertThat(access.allows(player(8), Map.of(8L, 1L))).isTrue();
        assertThat(access.allows(player(8), Map.of())).isFalse();
        assertThat(access.allows(player(2), Map.of(8L, 1L))).isFalse();
    }

    @Test
    void enablesAllAuthenticatedPlayersWithoutGrantingOwnerOrAnonymousAccess() {
        org.springframework.test.util.ReflectionTestUtils.setField(access, "allPlayers", true);
        assertThat(access.allows(player(2), Map.of())).isTrue();
        assertThat(access.allows(null, Map.of())).isFalse();
        assertThat(access.allows(new UsernamePasswordAuthenticationToken(player(1).getPrincipal(), null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER"))), Map.of())).isFalse();
    }

    @Test
    void allowsGoogleLoginUsingVerifiedPlayerIdentityRatherThanGoogleAttributes() {
        var google = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_PLAYER")),
                Map.of("sub", "google", "playerId", 1L), "sub");
        var selected = new OAuth2PlayerPrincipal(google, (PlayerPrincipal) player(1).getPrincipal());
        var other = new OAuth2PlayerPrincipal(google, (PlayerPrincipal) player(2).getPrincipal());
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
        assertThat(access.allows(playerAuthentication(transientPlayer), Map.of())).isFalse();
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
