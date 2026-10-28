package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Письма при изменении статуса регистрации (LFPT-0752): отмена, перевод в резерв
 * администратором, перевод из резерва в основной состав администратором.
 */
@ExtendWith(MockitoExtension.class)
class TournamentServiceStatusChangeNotificationTest {

    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private ClubService clubService;
    @Mock private EmailService emailService;

    private TournamentService service;

    private PlayerPadel player;
    private Tournament tournament;

    private void initService() {
        service = new TournamentService(
                tournamentRepository, registrationRepository, null, null,
                clubService, null, null, emailService, null,
                null, null, null, null, null, null);
    }

    private void commonStubs() {
        initService();
        lenient().when(clubService.getClubById(anyLong())).thenReturn(Optional.empty());
        lenient().when(registrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(registrationRepository.saveAll(any())).thenReturn(List.of());
        lenient().when(registrationRepository.findByTournamentIdAndStatusOrderByPositionAsc(anyLong(), any()))
                .thenReturn(List.of());
        lenient().when(registrationRepository.findByTournamentIdAndStatusOrderByWaitlistPositionAsc(anyLong(), any()))
                .thenReturn(List.of());
        lenient().when(registrationRepository.findMaxWaitlistPosition(anyLong())).thenReturn(Optional.of(0));
    }

    private Tournament aTournament() {
        LocalDateTime start = LocalDateTime.now().plusDays(2);
        return Tournament.builder()
                .id(1L).nombre("Torneo de prueba")
                .modalidad(Modalidad.INDIVIDUAL).tipo(TournamentType.AMERICANO)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .cupoMax(16).clubId(5L)
                .fechaInicio(start.toLocalDate()).horaInicio(start.toLocalTime())
                .isActive(true).build();
    }

    private PlayerPadel aPlayer() {
        return PlayerPadel.builder().id(2L).email("jugador@example.com").nombre("Jugador").build();
    }

    @Test
    void cancelRegistration_autocancelacionDelJugador_enviaEmailDeCancelacionConMotivo() {
        commonStubs();
        tournament = aTournament();
        player = aPlayer();
        // Estado inicial WAITLIST (no CONFIRMED) para no disparar processWaitlistForTournament —
        // el envío del email es incondicional, no depende del estado anterior.
        TournamentRegistration reg = TournamentRegistration.builder()
                .id(12L).tournament(tournament).player(player)
                .status(RegistrationStatus.WAITLIST).isActive(true).build();

        when(registrationRepository.findByTournamentIdAndPlayerId(1L, 2L)).thenReturn(Optional.of(reg));
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));

        service.cancelRegistration(1L, 2L, "No puedo asistir");

        assertThat(reg.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(reg.getIsActive()).isFalse();
        verify(emailService).sendRegistrationCancelledEmail(
                eq(player.getEmail()), eq(player.getNombre()), eq(tournament.getNombre()),
                any(), any(), any(), eq("No puedo asistir"), eq(player.getLocale()));
    }

    @Test
    void moveToWaitlist_confirmado_enviaEmailDeMovidoAReserva_noDeListaDeEsperaGenerico() {
        commonStubs();
        tournament = aTournament();
        player = aPlayer();
        TournamentRegistration reg = TournamentRegistration.builder()
                .id(10L).tournament(tournament).player(player)
                .status(RegistrationStatus.CONFIRMED).position(1).isActive(true).build();

        when(registrationRepository.findByTournamentIdAndPlayerId(1L, 2L)).thenReturn(Optional.of(reg));
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.countByTournamentIdAndStatus(1L, RegistrationStatus.CONFIRMED)).thenReturn(5L);

        service.moveToWaitlist(1L, 2L);

        assertThat(reg.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        verify(emailService).sendMovedToReserveEmail(
                eq(player.getEmail()), eq(player.getNombre()), eq(tournament.getNombre()),
                any(), any(), any(), eq(player.getLocale()));
        verify(emailService, never())
                .sendWaitlistNotificationEmail(any(), any(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void moveToMain_enReserva_enviaEmailDeConfirmacion() {
        commonStubs();
        tournament = aTournament();
        player = aPlayer();
        TournamentRegistration reg = TournamentRegistration.builder()
                .id(11L).tournament(tournament).player(player)
                .status(RegistrationStatus.WAITLIST).waitlistPosition(1).isActive(true).build();

        when(registrationRepository.findByTournamentIdAndPlayerId(1L, 2L)).thenReturn(Optional.of(reg));
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.countByTournamentIdAndStatus(1L, RegistrationStatus.CONFIRMED)).thenReturn(5L);

        service.moveToMain(1L, 2L);

        assertThat(reg.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        verify(emailService).sendWaitlistAutoConfirmEmail(
                eq(player.getEmail()), eq(player.getNombre()), eq(tournament.getNombre()),
                any(), any(), any(), any(), eq(player.getLocale()));
    }

    @Test
    void cancelPairRegistration_parejaCompleta_enviaEmailDeCancelacionAAmbosJugadores() {
        commonStubs();
        tournament = aTournament();
        tournament.setModalidad(Modalidad.DOBLES);
        PlayerPadel mainPlayer = aPlayer();
        PlayerPadel partner = PlayerPadel.builder().id(3L).email("partner@example.com").nombre("Compañero").build();

        TournamentRegistration mainReg = TournamentRegistration.builder()
                .id(20L).tournament(tournament).player(mainPlayer).partner(partner)
                .status(RegistrationStatus.CONFIRMED).position(1).isActive(true).isDoubleRegistration(true).build();
        TournamentRegistration partnerReg = TournamentRegistration.builder()
                .id(21L).tournament(tournament).player(partner).partner(mainPlayer)
                .mainPlayerId(mainPlayer.getId())
                .status(RegistrationStatus.CONFIRMED).position(1).isActive(true).isDoubleRegistration(true).build();

        when(registrationRepository.findByTournamentId(1L)).thenReturn(List.of(mainReg, partnerReg));
        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        // confirmedCount >= cupoMax de la prueba para no disparar processWaitlistForTournament
        when(registrationRepository.countByTournamentIdAndStatus(1L, RegistrationStatus.CONFIRMED)).thenReturn(16L);

        service.cancelPairRegistration(1L, mainPlayer.getId(), "No pueden asistir");

        verify(emailService).sendRegistrationCancelledEmail(
                eq(mainPlayer.getEmail()), eq(mainPlayer.getNombre()), eq(tournament.getNombre()),
                any(), any(), any(), eq("No pueden asistir"), eq(mainPlayer.getLocale()));
        verify(emailService).sendRegistrationCancelledEmail(
                eq(partner.getEmail()), eq(partner.getNombre()), eq(tournament.getNombre()),
                any(), any(), any(), eq("No pueden asistir"), eq(partner.getLocale()));
    }
}
