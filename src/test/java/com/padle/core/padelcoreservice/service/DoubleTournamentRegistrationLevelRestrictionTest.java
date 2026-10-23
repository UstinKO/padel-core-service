package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.exception.PlayerLevelRequiredException;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.mapper.TournamentRegistrationMapper;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * LFPT-0303: integración del chequeo de nivel en los puntos de auto-registro de
 * {@link DoubleTournamentRegistrationService} (registro de pareja con compañero
 * ya existente, y soло-registro). La lógica pura está en {@link TournamentLevelPolicyTest}.
 */
@ExtendWith(MockitoExtension.class)
class DoubleTournamentRegistrationLevelRestrictionTest {

    private static final long TOURNAMENT_ID = 1L;
    private static final long MAIN_PLAYER_ID = 2L;
    private static final long PARTNER_ID = 3L;

    @Mock private PlayerRepository playerRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private TournamentRegistrationMapper registrationMapper;
    @Mock private TournamentNotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private TournamentRepository tournamentRepository;
    @Mock private MessageSource messageSource;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private DoubleTournamentRegistrationService service;

    private TournamentDto tournamentDto;
    private PlayerPadel mainPlayer;

    @BeforeEach
    void setUp() {
        tournamentDto = TournamentDto.builder()
                .id(TOURNAMENT_ID)
                .modalidad(Modalidad.DOBLES)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .tipo(TournamentType.AMERICANO_TEAMS)
                .categoriaNivel(Nivel.C6.name())
                .cupoMax(16)
                .build();

        mainPlayer = PlayerPadel.builder()
                .id(MAIN_PLAYER_ID)
                .email("main@example.com")
                .nivelJugador(Nivel.C6) // dentro de rango para el torneo — aísla el chequeo del partner
                .build();

        when(playerRepository.findById(MAIN_PLAYER_ID)).thenReturn(Optional.of(mainPlayer));
    }

    @Test
    void jugadorPrincipalFueraDeRango_bloqueaAntesDeBuscarCompanero() {
        mainPlayer.setNivelJugador(Nivel.C9); // C9 vs C6 — demasiado lejos

        PartnerRegistrationDto partnerDto = new PartnerRegistrationDto();
        partnerDto.setNombre("Pareja");
        partnerDto.setApellido("Invitada");
        partnerDto.setEmail("partner@example.com");

        when(messageSource.getMessage(eq("tournament.register.level_mismatch"), any(), any(Locale.class)))
                .thenReturn("nivel lejano");

        assertThatThrownBy(() -> service.registerForDoubleTournament(tournamentDto, MAIN_PLAYER_ID, partnerDto))
                .isInstanceOf(TournamentRegistrationException.class)
                .isNotInstanceOf(PlayerLevelRequiredException.class);

        org.mockito.Mockito.verifyNoInteractions(registrationRepository);
    }

    @Test
    void companeroExistenteFueraDeRango_bloqueaAntesDeRegistrar() {
        PlayerPadel partner = PlayerPadel.builder()
                .id(PARTNER_ID)
                .email("partner@example.com")
                .nivelJugador(Nivel.C9) // C9 vs C6 — demasiado lejos
                .build();

        PartnerRegistrationDto partnerDto = new PartnerRegistrationDto();
        partnerDto.setNombre("Pareja");
        partnerDto.setApellido("Existente");
        partnerDto.setExistingUserId(PARTNER_ID);

        when(playerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
        when(messageSource.getMessage(eq("tournament.register.level_mismatch"), any(), any(Locale.class)))
                .thenReturn("nivel lejano");

        assertThatThrownBy(() -> service.registerForDoubleTournament(tournamentDto, MAIN_PLAYER_ID, partnerDto))
                .isInstanceOf(TournamentRegistrationException.class);
    }

    @Test
    void companeroSinCuenta_noBloqueaPorNivel_seRegistraDeInmediatoConNivelSinEspecificar() {
        // Sin existingUserId y sin email/teléfono coincidente con ningún jugador —
        // findPartner no encuentra cuenta, el nivel del compañero aún no existe (SIN_ESPECIFICAR
        // se asigna al crear la cuenta en registerWithNewPartner, LFPT-0687: la pareja se
        // registra de inmediato, sin esperar confirmación) — esta llamada no debe lanzar
        // PlayerLevelRequiredException/level_mismatch por el compañero.
        PartnerRegistrationDto partnerDto = new PartnerRegistrationDto();
        partnerDto.setNombre("Pareja");
        partnerDto.setApellido("Nueva");
        partnerDto.setTelefono("+5492222222222");
        partnerDto.setEmail("nueva-pareja@example.com");

        when(playerRepository.findByEmail("nueva-pareja@example.com")).thenReturn(Optional.empty());
        // LFPT-0720: findPartnerByContact ahora usa la búsqueda normalizada de teléfono/Telegram.
        when(playerRepository.findByNormalizedTelefono(any())).thenReturn(Optional.empty());
        when(playerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationMapper.toDto(any())).thenReturn(null);

        // No debe lanzar ninguna excepción relacionada con nivel (puede completar el registro)
        service.registerForDoubleTournament(tournamentDto, MAIN_PLAYER_ID, partnerDto);
    }

    @Test
    void registroSolo_jugadorFueraDeRango_bloquea() {
        Tournament tournament = Tournament.builder()
                .id(TOURNAMENT_ID)
                .modalidad(Modalidad.DOBLES)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .tipo(TournamentType.AMERICANO_TEAMS)
                .categoriaNivel(Nivel.C6)
                .fechaInicio(LocalDate.now().plusDays(7))
                .cupoMax(16)
                .build();
        mainPlayer.setNivelJugador(Nivel.C9);

        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament));
        when(messageSource.getMessage(eq("tournament.register.level_mismatch"), any(), any(Locale.class)))
                .thenReturn("nivel lejano");

        com.padle.core.padelcoreservice.dto.SoloDoubleRegistrationDto dto =
                new com.padle.core.padelcoreservice.dto.SoloDoubleRegistrationDto();
        dto.setSoloType("SEARCH");

        assertThatThrownBy(() -> service.registerSoloForDoubleTournament(TOURNAMENT_ID, MAIN_PLAYER_ID, dto))
                .isInstanceOf(TournamentRegistrationException.class);

        org.mockito.Mockito.verifyNoInteractions(registrationRepository);
    }
}
