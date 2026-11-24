package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerTelegramPilotTest {
    static PlayerTelegramProperties properties(Long playerId, Long eventId) {
        return new PlayerTelegramProperties(true, "OnePadelTestBot", true, 24, 3,
                1L, 123L, false, "fixture-token", true, playerId, eventId);
    }

    @Test void enabledIntegrationWithoutPilotIdsStillDeniesEveryone() {
        var bound = new Binder(new MapConfigurationPropertySource(Map.of("telegram.players.enabled", "true")))
                .bind("telegram.players", PlayerTelegramProperties.class).orElseThrow(IllegalStateException::new);
        var access = new PlayerTelegramAccess(bound);
        assertThat(access.pilot()).isTrue();
        assertThat(access.allowsPlayer(42L)).isFalse();
        assertThat(access.allowsEvent(10L)).isFalse();
        assertThat(access.allowsPlayer(null)).isFalse();
        assertThat(access.allowsEvent(null)).isFalse();
    }

    @Test void anotherAccountCannotIssueALinkOrChangePreferencesEvenWithASavedConnection() {
        var api = mock(PlayerTelegramApi.class);
        var store = mock(PlayerTelegramStore.class);
        var config = properties(42L, 10L);
        var links = new PlayerTelegramLinks(config, api, store, new PlayerTelegramAccess(config));
        when(api.available()).thenReturn(true);
        var outsider = PlayerPadel.builder().id(99L).activo(true).email("outsider@example.test").build();
        when(store.preference(99L)).thenReturn(ContactPreference.UNSPECIFIED);
        var status = links.status(outsider);
        assertThat(status.available()).isFalse();
        assertThat(status.connected()).isFalse();
        assertThat(status.eligible()).isFalse();
        assertThatThrownBy(() -> links.issue(outsider, Locale.ENGLISH)).isInstanceOf(AccountException.class);
        assertThatThrownBy(() -> links.preference(outsider, ContactPreference.WHATSAPP)).isInstanceOf(AccountException.class);
        verify(store, never()).token(anyLong(), anyString(), any(), anyString());
        verify(store, never()).preference(anyLong(), any(ContactPreference.class));
        verify(store, never()).connection(anyLong());
    }

    @Test void anUnexpiredOldTokenCannotBindAnotherAccountAfterPilotIsEnabled() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class), eq("old-token"))).thenReturn(List.of(99L));
        var store = new PlayerTelegramStore(jdbc, new PlayerTelegramAccess(properties(42L, 10L)));
        assertThat(store.bind("old-token", 123L, 123L)).isEmpty();
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test void outboundGuardRejectsOtherChatsUnknownChatsAndOtherEventsBeforeClaimingDelivery() {
        var store = mock(PlayerTelegramStore.class);
        var api = mock(PlayerTelegramApi.class);
        when(api.available()).thenReturn(true);
        when(store.byUser(999L)).thenReturn(Optional.of(new Connection(99L, 999L, 999L, true)));
        when(store.byUser(123L)).thenReturn(Optional.of(new Connection(42L, 123L, 123L, true)));
        var delivery = new PlayerTelegramDelivery(store, api, new PlayerTelegramAccess(properties(42L, 10L)));
        delivery.sendOnce("other-player", 10L, 20L, 999L, "Fixture", List.of());
        delivery.sendOnce("unknown-chat", null, null, 789L, "Fixture", List.of());
        delivery.sendOnce("other-event", 11L, 21L, 123L, "Fixture", List.of());
        verify(store, never()).claim(anyString(), any(), any(), anyLong());
        verify(api, never()).send(anyLong(), anyString(), anyList());
    }

    @Test void onlyTheLinkedPilotReceivesNotificationsAndOrganizerSummaries() {
        var store = mock(PlayerTelegramStore.class);
        var api = mock(PlayerTelegramApi.class);
        when(api.available()).thenReturn(true);
        when(store.byUser(123L)).thenReturn(Optional.of(new Connection(42L, 123L, 123L, true)));
        when(store.claim(eq("summary"), isNull(), isNull(), eq(123L))).thenReturn(true);
        when(api.send(123L, "Fixture summary", List.of())).thenReturn(new Result("SENT", 17L));
        new PlayerTelegramDelivery(store, api, new PlayerTelegramAccess(properties(42L, 10L)))
                .sendOnce("summary", null, null, 123L, "Fixture summary", List.of());
        verify(api).send(123L, "Fixture summary", List.of());
        verify(store).finish("summary", new Result("SENT", 17L));
    }
}
