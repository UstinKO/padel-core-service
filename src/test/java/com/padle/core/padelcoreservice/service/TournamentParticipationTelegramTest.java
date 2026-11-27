package com.padle.core.padelcoreservice.service;

import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TournamentParticipationTelegramTest {
    private final ParticipationRequestRepository requests = mock(ParticipationRequestRepository.class);
    private final TournamentRepository tournaments = mock(TournamentRepository.class);
    private final PairTermsService terms = mock(PairTermsService.class, CALLS_REAL_METHODS);
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
                .estado(TournamentStatus.REGISTRO_ABIERTO).modalidad(Modalidad.INDIVIDUAL)
                .tipo(TournamentType.CANCHA_ABIERTA).build();
        registration = TournamentRegistration.builder().id(20L).tournament(tournament).player(player)
                .status(RegistrationStatus.CONFIRMED).build();
        request = new ParticipationRequest(registration, null, ParticipationRequest.Trigger.ORGANIZER, OffsetDateTime.now());
        when(requests.findTournamentId(request.getId())).thenReturn(Optional.of(10L));
        when(tournaments.lockById(10L)).thenReturn(Optional.of(tournament));
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
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
    @Test void verifiedTelegramCanAnswerAfterTheEmailLinkExpired() {
        request = new ParticipationRequest(registration, null, ParticipationRequest.Trigger.ORGANIZER,
                OffsetDateTime.now().minusDays(8));
        when(requests.findTournamentId(request.getId())).thenReturn(Optional.of(10L));
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
        long start = TelegramParticipationCoordinator.startsAt(tournament).getEpochSecond();
        assertThat(service.confirmTelegram(request.getId(), player, start).status())
                .isEqualTo(ParticipationStatus.CONFIRMED);
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

    @ParameterizedTest
    @EnumSource(value = TournamentType.class, names = {"CANCHA_ABIERTA", "PADEL_CLINIC", "AMERICANO", "KING_OF_COURT"})
    void individualFormatsCanConfirmWithoutEnablingPairCancellation(TournamentType type) {
        tournament.setTipo(type);
        assertThat(terms.isCancellable(tournament)).isFalse();
        long start = TelegramParticipationCoordinator.startsAt(tournament).getEpochSecond();

        var answer = service.confirmTelegram(request.getId(), player, start);

        assertThat(answer.status()).isEqualTo(ParticipationStatus.CONFIRMED);
        assertThat(answer.canDecline()).isFalse();
        assertThat(registration.getParticipationConfirmed()).isFalse();
        assertThat(registration.getPayments()).isEmpty();
    }

    @Test void individualRequestCannotUsePairDeclineRules() {
        assertThat(service.review(request.getId(), player, false).canDecline()).isFalse();
        assertThatThrownBy(() -> service.answer(request.getId(), player,
                com.padle.core.padelcoreservice.dto.ParticipationDtos.Decision.DECLINE, false))
                .isInstanceOf(AccountException.class);
        assertThat(request.getStatus()).isEqualTo(ParticipationStatus.PENDING);
        assertThat(registration.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test void pairCanStillConfirmAndUseItsExistingDeclineRules() {
        tournament.setModalidad(Modalidad.DOBLES);
        tournament.setTipo(TournamentType.AMERICANO_TEAMS);
        tournament.setDeadlineCancelacion(tournament.getFechaHoraInicioCompleta().minusHours(1));
        assertThat(terms.isCancellable(tournament)).isTrue();
        long start = TelegramParticipationCoordinator.startsAt(tournament).getEpochSecond();

        var answer = service.confirmTelegram(request.getId(), player, start);

        assertThat(answer.status()).isEqualTo(ParticipationStatus.CONFIRMED);
        assertThat(answer.canDecline()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TournamentStatus.class, names = {"BORRADOR", "CANCELADO", "FINALIZADO"})
    void unavailableIndividualEventCannotBeConfirmed(TournamentStatus status) {
        tournament.setEstado(status);
        long start = TelegramParticipationCoordinator.startsAt(tournament).getEpochSecond();
        assertThatThrownBy(() -> service.confirmTelegram(request.getId(), player, start))
                .isInstanceOf(AccountException.class);
        assertThat(request.getStatus()).isEqualTo(ParticipationStatus.PENDING);
    }

    @Test void startedIndividualEventUsesItsOwnTimeZone() {
        var started = ZonedDateTime.now(ZoneId.of(tournament.getTimeZone())).minusMinutes(5);
        tournament.setFechaInicio(started.toLocalDate());
        tournament.setHoraInicio(started.toLocalTime());
        long start = TelegramParticipationCoordinator.startsAt(tournament).getEpochSecond();
        assertThatThrownBy(() -> service.confirmTelegram(request.getId(), player, start))
                .isInstanceOf(AccountException.class);
        assertThat(request.getStatus()).isEqualTo(ParticipationStatus.PENDING);
    }
}
