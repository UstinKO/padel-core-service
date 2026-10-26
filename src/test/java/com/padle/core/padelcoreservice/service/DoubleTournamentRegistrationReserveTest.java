package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import com.padle.core.padelcoreservice.dto.ReserveCancelPreviewDto;
import com.padle.core.padelcoreservice.dto.SoloDoubleRegistrationDto;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.mapper.TournamentRegistrationMapper;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Перевод "Agrega compañero luego" в резерв по точному дедлайну (старт - 24ч),
 * возврат в основной состав с учётом очерёдности резерва, self-service действия по токену.
 */
@ExtendWith(MockitoExtension.class)
class DoubleTournamentRegistrationReserveTest {

    private static final long TOURNAMENT_ID = 1L;
    private static final long PLAYER_ID = 2L;
    private static final long PARTNER_ID = 3L;

    @Mock private PlayerRepository playerRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private TournamentRegistrationMapper registrationMapper;
    @Mock private TournamentNotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private TournamentRepository tournamentRepository;
    @Mock private MessageSource messageSource;
    @Mock private TournamentService tournamentService;
    @Mock private ClubRepository clubRepository;

    @InjectMocks private DoubleTournamentRegistrationService service;

    private PlayerPadel player;

    @BeforeEach
    void setUp() {
        player = PlayerPadel.builder()
                .id(PLAYER_ID)
                .email("player@example.com")
                .nombre("Jugador")
                .nivelJugador(Nivel.C6)
                .build();
    }

    private Tournament tournamentStartingIn(long hoursFromNow) {
        var now = java.time.LocalDateTime.now().plusHours(hoursFromNow);
        return Tournament.builder()
                .id(TOURNAMENT_ID)
                .modalidad(Modalidad.DOBLES)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .tipo(TournamentType.AMERICANO_TEAMS)
                .categoriaNivel(Nivel.TODOS)
                .cupoMax(16)
                .fechaInicio(now.toLocalDate())
                .horaInicio(now.toLocalTime())
                .build();
    }

    @Test
    void registrarSolo_dedlineYaPaso_vaDirectoAReservaConToken() {
        Tournament tournament = tournamentStartingIn(1); // старт через 1ч → дедлайн (старт-24ч) давно прошёл
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament));
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(registrationRepository.findByTournamentIdAndPlayerId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(Optional.empty());
        when(registrationRepository.findByTournamentIdAndPlayerIdAndIsActiveFalse(TOURNAMENT_ID, PLAYER_ID)).thenReturn(Optional.empty());
        when(registrationRepository.countConfirmedPairs(TOURNAMENT_ID)).thenReturn(0L);
        when(registrationRepository.countActiveSoloRegistrations(TOURNAMENT_ID)).thenReturn(0L);
        when(registrationRepository.findMaxWaitlistPosition(TOURNAMENT_ID)).thenReturn(Optional.of(2));
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationMapper.toDto(any())).thenReturn(null);

        SoloDoubleRegistrationDto dto = new SoloDoubleRegistrationDto();
        dto.setSoloType("ADD_LATER");

        service.registerSoloForDoubleTournament(TOURNAMENT_ID, PLAYER_ID, dto);

        ArgumentCaptor<TournamentRegistration> captor = ArgumentCaptor.forClass(TournamentRegistration.class);
        verify(registrationRepository).save(captor.capture());
        TournamentRegistration saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(saved.getWaitlistPosition()).isEqualTo(3);
        assertThat(saved.getPartnerActionToken()).isNotBlank();

        verify(emailService).sendPartnerReserveEmail(
                eq(player.getEmail()), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void registrarSolo_dedlineNoPaso_quedaComoSoloAddLaterSinToken() {
        Tournament tournament = tournamentStartingIn(72); // старт en 72ч → дедлайн aún no llegó
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament));
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
        when(registrationRepository.findByTournamentIdAndPlayerId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(Optional.empty());
        when(registrationRepository.findByTournamentIdAndPlayerIdAndIsActiveFalse(TOURNAMENT_ID, PLAYER_ID)).thenReturn(Optional.empty());
        when(registrationRepository.countConfirmedPairs(TOURNAMENT_ID)).thenReturn(0L);
        when(registrationRepository.countActiveSoloRegistrations(TOURNAMENT_ID)).thenReturn(0L);
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationMapper.toDto(any())).thenReturn(null);

        SoloDoubleRegistrationDto dto = new SoloDoubleRegistrationDto();
        dto.setSoloType("ADD_LATER");

        service.registerSoloForDoubleTournament(TOURNAMENT_ID, PLAYER_ID, dto);

        ArgumentCaptor<TournamentRegistration> captor = ArgumentCaptor.forClass(TournamentRegistration.class);
        verify(registrationRepository).save(captor.capture());
        TournamentRegistration saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo(RegistrationStatus.SOLO_ADD_LATER);
        assertThat(saved.getWaitlistPosition()).isNull();
        assertThat(saved.getPartnerActionToken()).isNull();

        verify(emailService, never()).sendPartnerReserveEmail(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void addPartner_antesDeDeadline_confirmaDeInmediatoSinTocarWaitlist() {
        Tournament tournament = tournamentStartingIn(72);
        PlayerPadel partner = PlayerPadel.builder().id(PARTNER_ID).email("partner@example.com").nombre("Pareja")
                .nivelJugador(Nivel.C6).build();
        TournamentRegistration soloReg = TournamentRegistration.builder()
                .id(10L).tournament(tournament).player(player)
                .status(RegistrationStatus.SOLO_ADD_LATER).isActive(true).isDoubleRegistration(false)
                .build();

        when(registrationRepository.findByTournamentIdAndPlayerId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(Optional.of(soloReg));
        when(playerRepository.findByEmail("partner@example.com")).thenReturn(Optional.of(partner));
        when(registrationRepository.findByTournamentIdAndPlayerIdAndIsActiveFalse(TOURNAMENT_ID, PARTNER_ID)).thenReturn(Optional.empty());
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationMapper.toDto(any())).thenReturn(null);

        PartnerRegistrationDto partnerDto = new PartnerRegistrationDto();
        partnerDto.setEmail("partner@example.com");

        service.addPartnerToSoloRegistration(TOURNAMENT_ID, PLAYER_ID, partnerDto);

        assertThat(soloReg.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        verify(tournamentService, never()).processWaitlistForTournament(anyLong());
    }

    @Test
    void addPartner_despuesDeReserva_encolaYDelegaElProcesamientoDeLaWaitlist() {
        Tournament tournament = tournamentStartingIn(2);
        PlayerPadel partner = PlayerPadel.builder().id(PARTNER_ID).email("partner@example.com").nombre("Pareja")
                .nivelJugador(Nivel.C6).build();
        TournamentRegistration soloReg = TournamentRegistration.builder()
                .id(10L).tournament(tournament).player(player)
                .status(RegistrationStatus.WAITLIST).isActive(true).isDoubleRegistration(false)
                .waitlistPosition(1).partnerActionToken("PAT_X").build();

        when(registrationRepository.findByTournamentIdAndPlayerId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(Optional.of(soloReg));
        when(playerRepository.findByEmail("partner@example.com")).thenReturn(Optional.of(partner));
        when(registrationRepository.findByTournamentIdAndPlayerIdAndIsActiveFalse(TOURNAMENT_ID, PARTNER_ID)).thenReturn(Optional.empty());
        when(registrationRepository.findMaxWaitlistPosition(TOURNAMENT_ID)).thenReturn(Optional.of(5));
        when(registrationRepository.findById(10L)).thenReturn(Optional.of(soloReg));
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationMapper.toDto(any())).thenReturn(null);

        PartnerRegistrationDto partnerDto = new PartnerRegistrationDto();
        partnerDto.setEmail("partner@example.com");

        service.addPartnerToSoloRegistration(TOURNAMENT_ID, PLAYER_ID, partnerDto);

        assertThat(soloReg.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(soloReg.getWaitlistPosition()).isEqualTo(6); // va al final de la cola (max+1), no a su posición anterior
        verify(tournamentService, times(1)).processWaitlistForTournament(TOURNAMENT_ID);
    }

    @Test
    void addPartner_inscripcionYaNoPendiente_lanzaExcepcion() {
        Tournament tournament = tournamentStartingIn(72);
        PlayerPadel otherPartner = PlayerPadel.builder().id(99L).build();
        TournamentRegistration confirmedPairReg = TournamentRegistration.builder()
                .id(11L).tournament(tournament).player(player).partner(otherPartner)
                .status(RegistrationStatus.WAITLIST).isActive(true).isDoubleRegistration(true).build();

        when(registrationRepository.findByTournamentIdAndPlayerId(TOURNAMENT_ID, PLAYER_ID)).thenReturn(Optional.of(confirmedPairReg));

        PartnerRegistrationDto partnerDto = new PartnerRegistrationDto();
        partnerDto.setEmail("partner@example.com");

        assertThatThrownBy(() -> service.addPartnerToSoloRegistration(TOURNAMENT_ID, PLAYER_ID, partnerDto))
                .isInstanceOf(TournamentRegistrationException.class);
    }

    @Test
    void confirmarActualidad_tokenValido_noCambiaElEstado() {
        Tournament tournament = tournamentStartingIn(2);
        TournamentRegistration reg = TournamentRegistration.builder()
                .id(10L).tournament(tournament).player(player)
                .status(RegistrationStatus.WAITLIST).isActive(true).partnerActionToken("PAT_X").build();
        when(registrationRepository.findByPartnerActionToken("PAT_X")).thenReturn(Optional.of(reg));

        service.confirmReserveStillActive("PAT_X");

        assertThat(reg.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(reg.getIsActive()).isTrue();
    }

    @Test
    void confirmarActualidad_tokenDeInscripcionYaConPareja_lanzaExcepcion() {
        PlayerPadel partner = PlayerPadel.builder().id(PARTNER_ID).build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .id(10L).tournament(tournamentStartingIn(2)).player(player).partner(partner)
                .status(RegistrationStatus.WAITLIST).isActive(true).partnerActionToken("PAT_X").build();
        when(registrationRepository.findByPartnerActionToken("PAT_X")).thenReturn(Optional.of(reg));

        assertThatThrownBy(() -> service.confirmReserveStillActive("PAT_X"))
                .isInstanceOf(TournamentRegistrationException.class);
    }

    @Test
    void previsualizarCancelacion_tokenValido_devuelveDatosDelTorneo() {
        Tournament tournament = tournamentStartingIn(2);
        tournament.setNombre("Torneo de prueba");
        TournamentRegistration reg = TournamentRegistration.builder()
                .id(10L).tournament(tournament).player(player)
                .status(RegistrationStatus.WAITLIST).isActive(true).partnerActionToken("PAT_X").build();
        when(registrationRepository.findByPartnerActionToken("PAT_X")).thenReturn(Optional.of(reg));

        ReserveCancelPreviewDto preview = service.previewReserveCancellation("PAT_X");

        assertThat(preview.tournamentName()).isEqualTo("Torneo de prueba");
    }

    @Test
    void cancelarPorToken_inscripcionPendiente_laCancela() {
        TournamentRegistration reg = TournamentRegistration.builder()
                .id(10L).tournament(tournamentStartingIn(2)).player(player)
                .status(RegistrationStatus.WAITLIST).isActive(true).partnerActionToken("PAT_X").build();
        when(registrationRepository.findByPartnerActionToken("PAT_X")).thenReturn(Optional.of(reg));
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.cancelReserveByToken("PAT_X");

        assertThat(reg.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(reg.getIsActive()).isFalse();
    }

    @Test
    void cancelarPorToken_yaCancelada_esIdempotenteNoLanza() {
        TournamentRegistration reg = TournamentRegistration.builder()
                .id(10L).tournament(tournamentStartingIn(2)).player(player)
                .status(RegistrationStatus.CANCELLED).isActive(false).partnerActionToken("PAT_X").build();
        when(registrationRepository.findByPartnerActionToken("PAT_X")).thenReturn(Optional.of(reg));

        service.cancelReserveByToken("PAT_X"); // no debe lanzar

        verify(registrationRepository, never()).save(any());
    }
}
