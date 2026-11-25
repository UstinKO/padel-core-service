package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramAccess;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramStore;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramParticipationPilotTest {
    private final TournamentRepository tournaments = mock(TournamentRepository.class);
    private final TournamentRegistrationRepository registrations = mock(TournamentRegistrationRepository.class);
    private final ParticipationRequests requests = mock(ParticipationRequests.class);
    private final TournamentAccessService access = mock(TournamentAccessService.class);
    private final TelegramParticipationCoordinator service = new TelegramParticipationCoordinator(tournaments,
            registrations, mock(ParticipationRequestRepository.class), requests, access, mock(PlayerTelegramStore.class),
            new PlayerTelegramAccess(new PlayerTelegramProperties(true, "OnePadelTestBot", true, 24, 3,
                    null, null, false, "fixture-token", true, 42L, 10L)));

    @Test void manualAndAutomaticRequestsCannotTouchAnotherEvent() {
        assertThat(service.prepare(11L)).isEmpty();
        var owner = Owner.builder().id(1L).build();
        assertThatThrownBy(() -> service.request(11L, owner)).isInstanceOf(AccountException.class);
        verify(access).assertCanManageTournament(owner, 11L);
        verifyNoInteractions(tournaments, registrations, requests);
    }

    @Test void pilotEventWithOtherParticipantsCreatesARequestOnlyForTheTester() {
        var tournament = Tournament.builder().id(10L).fechaInicio(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.NOON).estado(TournamentStatus.REGISTRO_ABIERTO).build();
        var tester = row(20L, 42L, tournament);
        var outsider = row(21L, 99L, tournament);
        when(tournaments.lockById(10L)).thenReturn(Optional.of(tournament));
        when(registrations.findByTournamentId(10L)).thenReturn(List.of(tester, outsider));
        assertThat(service.prepare(10L)).containsExactly(20L);
        verify(requests).openTelegram(tester, ParticipationRequest.Trigger.ORGANIZER);
        verify(requests, never()).openTelegram(eq(outsider), any());
    }

    private TournamentRegistration row(long id, long playerId, Tournament event) {
        return TournamentRegistration.builder().id(id).tournament(event)
                .player(PlayerPadel.builder().id(playerId).activo(true).email("fixture" + playerId + "@example.test").build())
                .status(RegistrationStatus.CONFIRMED).build();
    }
}
