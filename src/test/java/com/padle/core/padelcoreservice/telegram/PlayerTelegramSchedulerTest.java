package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.service.TelegramParticipationCoordinator;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;

class PlayerTelegramSchedulerTest {
    private final PlayerTelegramLinks links = mock(PlayerTelegramLinks.class);
    private final TournamentRepository tournaments = mock(TournamentRepository.class);
    private final TelegramParticipationCoordinator participation = mock(TelegramParticipationCoordinator.class);
    private final PlayerTelegramNotifications notifications = mock(PlayerTelegramNotifications.class);
    private PlayerTelegramScheduler scheduler(boolean automatic) {
        return new PlayerTelegramScheduler(new PlayerTelegramProperties(true, "OnePadelTestBot", automatic, 24, 3,
                null, null, false), links, tournaments, participation, notifications, mock(PlayerTelegramSummary.class));
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
}
