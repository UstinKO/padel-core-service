package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Locale;
import com.padle.core.padelcoreservice.telegram.TelegramModels.ContactPreference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerTelegramLinksTest {
    private final PlayerTelegramApi api = mock(PlayerTelegramApi.class);
    private final PlayerTelegramStore store = mock(PlayerTelegramStore.class);
    private final PlayerTelegramProperties properties = new PlayerTelegramProperties(true, "OnePadelTestBot", false, 24, 3, null, null, false, "fixture-token");
    private final PlayerTelegramLinks links = new PlayerTelegramLinks(properties, api, store);

    @Test void linkIsOpaqueExpiresAndDoesNotStoreItsBearerToken() {
        when(api.available()).thenReturn(true);
        var player = PlayerPadel.builder().id(42L).activo(true).email("fixture@example.test").build();
        var link = links.issue(player, Locale.forLanguageTag("ru"));
        String token = link.url().substring(link.url().indexOf("start=") + 6);
        assertThat(token).matches("[A-Za-z0-9_-]{32}");
        var hash = ArgumentCaptor.forClass(String.class);
        verify(store).token(eq(42L), hash.capture(), eq(link.expiresAt()), eq("ru"));
        assertThat(hash.getValue()).hasSize(64).isNotEqualTo(token).isEqualTo(PlayerTelegramLinks.hash(token));
        assertThat(link.expiresAt()).isBetween(OffsetDateTime.now().plusMinutes(9), OffsetDateTime.now().plusMinutes(11));
    }

    @Test void groupIdentityAndMalformedPayloadNeverReachBinding() {
        assertThat(links.bind("a".repeat(32), 99, -100)).isEmpty();
        assertThat(links.bind("invalid", 99, 99)).isEmpty();
        verifyNoInteractions(store);
    }

    @Test void absenceOfUsernameDoesNotBlockLinking() {
        when(store.bind(anyString(), eq(99L), eq(99L))).thenReturn(Optional.of(42L));
        assertThat(links.bind("a".repeat(32), 99, 99)).contains(42L);
    }

    @Test void disabledIntegrationCannotIssueALink() {
        var player = PlayerPadel.builder().id(42L).activo(true).email("fixture@example.test").build();
        assertThatThrownBy(() -> links.issue(player, Locale.forLanguageTag("ru"))).isInstanceOf(AccountException.class);
        verifyNoInteractions(store);
    }

    @Test void preferenceIsSavedForTheAuthenticatedPlayerWithoutRequiringABotToken() {
        var player = PlayerPadel.builder().id(42L).activo(true).email("fixture@example.test").build();
        when(store.connection(42L)).thenReturn(Optional.empty());
        when(store.preference(42L)).thenReturn(ContactPreference.WHATSAPP);
        var status = links.preference(player, ContactPreference.WHATSAPP);
        verify(store).preference(42L, ContactPreference.WHATSAPP);
        assertThat(status.preference()).isEqualTo(ContactPreference.WHATSAPP);
        assertThat(status.connected()).isFalse();
    }

    @Test void unsupportedLinkLanguageFallsBackToSpanish() {
        when(api.available()).thenReturn(true);
        var player = PlayerPadel.builder().id(42L).activo(true).email("fixture@example.test").build();
        links.issue(player, Locale.FRENCH);
        verify(store).token(eq(42L), anyString(), any(), eq("es"));
    }
}
