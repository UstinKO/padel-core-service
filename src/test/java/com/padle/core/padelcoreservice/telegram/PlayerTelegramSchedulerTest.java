package com.padle.core.padelcoreservice.telegram;

import com.padle.core.application.clubs.InternalClubReadPort;
import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.service.TelegramParticipationCoordinator;
import com.padle.core.padelcoreservice.service.TelegramReminderScheduler;
import com.padle.core.padelcoreservice.service.TelegramService;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;

class PlayerTelegramSchedulerTest {
    private final PlayerTelegramLinks links = mock(PlayerTelegramLinks.class);
    private final TournamentRepository tournaments = mock(TournamentRepository.class);
    private final TelegramParticipationCoordinator participation = mock(TelegramParticipationCoordinator.class);
    private final PlayerTelegramNotifications notifications = mock(PlayerTelegramNotifications.class);
    private PlayerTelegramScheduler scheduler(boolean automatic) {
        return new PlayerTelegramScheduler(new PlayerTelegramProperties(true, "OnePadelTestBot", automatic, 24, 3,
                null, null, false, "fixture-token", false, null, null), links, tournaments, participation, notifications, mock(PlayerTelegramSummary.class),
                new PlayerTelegramAccess(new PlayerTelegramProperties(true, "OnePadelTestBot", automatic, 24, 3,
                        null, null, false, "fixture-token", false, null, null)));
    }
    private Tournament event(int hours, TournamentStatus status) {
        var start = Instant.now().plusSeconds(hours * 3600L).atZone(ZoneId.of("America/Argentina/Buenos_Aires"));
        return Tournament.builder().id(42L).fechaInicio(start.toLocalDate()).horaInicio(start.toLocalTime())
                .timeZone(start.getZone().getId()).estado(status).build();
    }
    @Test void lateRegistrationGetsOneReminderInsteadOfTwoMessages() {
        when(links.available()).thenReturn(true);
        when(tournaments.findByFechaInicioInAndActive(any())).thenReturn(List.of(event(2, TournamentStatus.REGISTRO_ABIERTO)));
        when(participation.prepare(42L)).thenReturn(List.of(81L));
        scheduler(true).tick();
        verify(notifications).send(81L, true);
        verifyNoMoreInteractions(notifications);
    }
    @Test void cancelledEventsAreNotSentEvenIfReturnedByRepository() {
        when(links.available()).thenReturn(true);
        when(tournaments.findByFechaInicioInAndActive(any())).thenReturn(List.of(event(2, TournamentStatus.CANCELADO)));
        scheduler(true).tick();
        verifyNoInteractions(participation, notifications);
    }
    @Test void manualModeDoesNotStartAnAutomaticBroadcast() {
        when(links.available()).thenReturn(true);
        scheduler(false).tick();
        verifyNoInteractions(tournaments, participation, notifications);
    }

    @Test void generalPlayerBotDoesNotFallBackToLegacyPersonalRemindersInManualMode() {
        var telegram = mock(TelegramService.class);
        var rows = mock(TournamentRegistrationRepository.class);
        var clubs = mock(InternalClubReadPort.class);
        var properties = new PlayerTelegramProperties(true, "OnePadelTestBot", false, 24, 3,
                null, null, false, "fixture-token", false, null, null);
        var event = event(2, TournamentStatus.REGISTRO_ABIERTO); event.setClubId(10L); event.setNombre("Учебный турнир");
        var player = PlayerPadel.builder().id(81L).telegramChatId(123L).build();
        when(tournaments.findById(42L)).thenReturn(Optional.of(event));
        when(rows.findByTournamentIdAndStatus(42L, RegistrationStatus.CONFIRMED))
                .thenReturn(List.of(TournamentRegistration.builder().player(player).build()));
        var legacy = new TelegramReminderScheduler(telegram, new PlayerTelegramAccess(properties), tournaments, rows, clubs);
        legacy.sendReminderByTournamentId(42L);
        verify(telegram, never()).sendDm(anyLong(), anyString(), anyList());
    }
}
