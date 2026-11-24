package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;

class PlayerTelegramDeliveryTest {
    private PlayerTelegramAccess access() {
        return new PlayerTelegramAccess(new PlayerTelegramProperties(
                true, "OnePadelTestBot", false, 24, 3, null, null, false, "fixture-token", false, null, null));
    }
    @Test void uncertainDeliveryIsRecordedAndDoesNotAutomaticallyRetry() {
        var store = mock(PlayerTelegramStore.class);
        var api = mock(PlayerTelegramApi.class);
        var service = new PlayerTelegramDelivery(store, api, access());
        when(api.available()).thenReturn(true);
        when(store.claim("cycle", 1L, 2L, 123L)).thenReturn(true, false);
        var outcome = new Result("UNKNOWN", null);
        when(api.send(123L, "Fixture", List.of())).thenReturn(outcome);
        service.sendOnce("cycle", 1L, 2L, 123L, "Fixture", List.of());
        service.sendOnce("cycle", 1L, 2L, 123L, "Fixture", List.of());
        verify(api, times(1)).send(123L, "Fixture", List.of());
        verify(store).finish("cycle", outcome);
    }
    @Test void blockedBotStopsFutureMessagesForTheConnection() {
        var store = mock(PlayerTelegramStore.class);
        var api = mock(PlayerTelegramApi.class);
        when(api.available()).thenReturn(true);
        when(store.claim("cycle", 1L, 2L, 123L)).thenReturn(true);
        when(api.send(anyLong(), anyString(), anyList())).thenReturn(new Result("BLOCKED", null));
        when(store.byUser(123L)).thenReturn(Optional.of(new Connection(42, 123, 123, true)));
        new PlayerTelegramDelivery(store, api, access()).sendOnce("cycle", 1L, 2L, 123L, "Fixture", List.of());
        verify(store).disconnect(42);
    }
}
