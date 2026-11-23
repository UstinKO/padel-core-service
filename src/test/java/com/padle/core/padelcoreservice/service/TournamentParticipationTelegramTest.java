package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TournamentParticipationTelegramTest {
    private final ParticipationRequestRepository requests = mock(ParticipationRequestRepository.class);
    private final TournamentRepository tournaments = mock(TournamentRepository.class);
    private final PairTermsService terms = mock(PairTermsService.class);
    private final TournamentParticipationService service = new TournamentParticipationService(requests, tournaments,
            mock(TournamentRegistrationRepository.class), mock(TournamentService.class), terms,
            mock(TournamentAccessService.class), mock(ParticipationRequests.class));
    private final PlayerPadel player = PlayerPadel.builder().id(42L).email("fixture@gmail.com").build();
    private Tournament tournament;
    private TournamentRegistration registration;
    private ParticipationRequest request;
    @BeforeEach void setup() {
        tournament = Tournament.builder().id(10L).fechaInicio(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.NOON).timeZone("America/Argentina/Buenos_Aires")
                .estado(TournamentStatus.REGISTRO_ABIERTO).build();
        registration = TournamentRegistration.builder().id(20L).tournament(tournament).player(player)
                .status(RegistrationStatus.CONFIRMED).build();
        request = new ParticipationRequest(registration, null, ParticipationRequest.Trigger.ORGANIZER, OffsetDateTime.now());
        when(requests.findTournamentId(request.getId())).thenReturn(Optional.of(10L));
        when(tournaments.lockById(10L)).thenReturn(Optional.of(tournament));
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        when(terms.isCancellable(tournament)).thenReturn(true);
    }
    @Test void confirmsSeparatelyFromRegistrationAndRepeatIsSafe() {
        long start = TelegramParticipationCoordinator.startsAt(tournament).getEpochSecond();
        assertThat(service.confirmTelegram(request.getId(), player, start).status()).isEqualTo(ParticipationStatus.CONFIRMED);
        var respondedAt = request.getRespondedAt();
        assertThat(service.confirmTelegram(request.getId(), player, start).status()).isEqualTo(ParticipationStatus.CONFIRMED);
        assertThat(request.getRespondedAt()).isEqualTo(respondedAt);
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registration.getParticipationConfirmed()).isFalse();
        assertThat(registration.getPayments()).isEmpty();
    }
    @Test void movedEventCannotBeConfirmedFromAnOldButton() {
        long obsoleteStart = TelegramParticipationCoordinator.startsAt(tournament).minusSeconds(3600).getEpochSecond();
        assertThatThrownBy(() -> service.confirmTelegram(request.getId(), player, obsoleteStart)).isInstanceOf(AccountException.class);
        assertThat(request.getStatus()).isEqualTo(ParticipationStatus.PENDING);
    }
    @Test void anotherPlayerCannotUseTheButton() {
        var stranger = PlayerPadel.builder().id(99L).email("stranger@gmail.com").build();
        long start = TelegramParticipationCoordinator.startsAt(tournament).getEpochSecond();
        assertThatThrownBy(() -> service.confirmTelegram(request.getId(), stranger, start)).isInstanceOf(AccountException.class);
        assertThat(request.getStatus()).isEqualTo(ParticipationStatus.PENDING);
    }
    @Test void cancelledAndRegisteredAgainIsADifferentCycle() {
        registration.setCancellationVersion(1L);
        long start = TelegramParticipationCoordinator.startsAt(tournament).getEpochSecond();
        assertThat(service.confirmTelegram(request.getId(), player, start).outdated()).isTrue();
        assertThat(request.getStatus()).isEqualTo(ParticipationStatus.PENDING);
    }
}
