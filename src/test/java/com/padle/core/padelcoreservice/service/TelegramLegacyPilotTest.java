package com.padle.core.padelcoreservice.service;

import com.padle.core.application.clubs.InternalClubReadPort;
import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.config.TelegramProperties;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramAccess;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramPoller;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.mockito.Mockito.*;

class TelegramLegacyPilotTest {
    private final PlayerTelegramProperties config = new PlayerTelegramProperties(true, "OnePadelTestBot", false,
            24, 3, null, null, false, "fixture-token", true, 42L, 10L);
    private final PlayerTelegramAccess access = new PlayerTelegramAccess(config);

    @Test void oldRemindersContinueForOthersAndSkipThePilotProfile() {
        var telegram = mock(TelegramService.class);
        var tournaments = mock(TournamentRepository.class);
        var registrations = mock(TournamentRegistrationRepository.class);
        var event = Tournament.builder().id(10L).clubId(1L).nombre("Fixture").horaInicio(LocalTime.NOON).build();
        var pilot = PlayerPadel.builder().id(42L).telegramChatId(123L).build();
        var other = PlayerPadel.builder().id(99L).telegramChatId(999L).build();
        when(tournaments.findById(10L)).thenReturn(Optional.of(event));
        when(registrations.findByTournamentIdAndStatus(10L, RegistrationStatus.CONFIRMED)).thenReturn(List.of(
                TournamentRegistration.builder().player(pilot).build(), TournamentRegistration.builder().player(other).build()));
        new TelegramReminderScheduler(telegram, access, tournaments, registrations, mock(InternalClubReadPort.class))
                .sendReminderByTournamentId(10L);
        verify(telegram).sendDm(eq(999L), anyString(), anyList());
        verify(telegram, never()).sendDm(eq(123L), anyString(), anyList());
    }

    @Test void oldPollingContinuesAlongsideNewBotForNonPilotProfiles() {
        var oldConfig = new TelegramProperties();
        oldConfig.setEnabled(true);
        oldConfig.setBotToken("technical-fixture-token");
        var players = mock(PlayerRepository.class);
        var newPoller = mock(PlayerTelegramPoller.class);
        var http = mock(RestTemplate.class);
        var player = PlayerPadel.builder().id(99L).nombre("Fixture").build();
        when(players.findByTelegramUsername("fixture")).thenReturn(Optional.of(player));
        when(http.getForObject(anyString(), eq(Map.class))).thenReturn(Map.of("ok", true, "result", List.of(
                Map.of("update_id", 1, "message", Map.of("text", "/start", "from", Map.of("username", "fixture"),
                        "chat", Map.of("id", 999L))))));
        new TelegramBotPoller(oldConfig, config, newPoller, access, players, http).pollUpdates();
        verify(newPoller).poll();
        verify(players).save(player);
    }
}
