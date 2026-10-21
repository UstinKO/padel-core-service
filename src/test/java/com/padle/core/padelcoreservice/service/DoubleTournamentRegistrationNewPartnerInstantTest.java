package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LFPT-0687: пара, записанная игроком с партнёром без аккаунта (контакт по телефону/Telegram,
 * без email), регистрируется немедленно — без ожидания email-подтверждения от партнёра или
 * действия координатора. Статус пары определяется занятостью турнира (CONFIRMED/WAITLIST),
 * как и для уже существующего партнёра — без отдельного промежуточного состояния.
 */
@ExtendWith(MockitoExtension.class)
class DoubleTournamentRegistrationNewPartnerInstantTest {

    private static final long TOURNAMENT_ID = 1L;
    private static final long MAIN_PLAYER_ID = 2L;

    @Mock private PlayerRepository playerRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private TournamentRegistrationMapper registrationMapper;
    @Mock private TournamentNotificationService notificationService;
    @Mock private EmailService emailService;
    @Mock private TournamentRepository tournamentRepository;
    @Mock private MessageSource messageSource;
    @Mock private TournamentService tournamentService;
    @Mock private ClubRepository clubRepository;
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
                .categoriaNivel(Nivel.TODOS.name())
                .cupoMax(16)
                .build();

        mainPlayer = PlayerPadel.builder()
                .id(MAIN_PLAYER_ID)
                .email("main@example.com")
                .nombre("Principal")
                .nivelJugador(Nivel.C6)
                .build();

        when(playerRepository.findById(MAIN_PLAYER_ID)).thenReturn(Optional.of(mainPlayer));
        org.mockito.Mockito.lenient().when(playerRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(playerRepository.findByTelefono(any())).thenReturn(Optional.empty());
        when(registrationRepository.findByTournamentIdAndPlayerId(eq(TOURNAMENT_ID), anyLong()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(registrationMapper.toDto(any())).thenReturn(null);
    }

    @Test
    void parejaConCompaneroSinCuenta_haySlot_quedaConfirmadaDeInmediatoConTelegram() {
        when(registrationRepository.countConfirmedPairs(TOURNAMENT_ID)).thenReturn(0L);
        when(playerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PartnerRegistrationDto partnerDto = new PartnerRegistrationDto();
        partnerDto.setNombre("Pareja");
        partnerDto.setApellido("Nueva");
        partnerDto.setTelefono("+5492222222222");
        partnerDto.setTelegramUsername("pareja_tg");

        service.registerForDoubleTournament(tournamentDto, MAIN_PLAYER_ID, partnerDto);

        ArgumentCaptor<PlayerPadel> playerCaptor = ArgumentCaptor.forClass(PlayerPadel.class);
        verify(playerRepository).save(playerCaptor.capture());
        PlayerPadel createdPartner = playerCaptor.getValue();
        assertThat(createdPartner.getTelegramUsername()).isEqualTo("pareja_tg");
        assertThat(createdPartner.getNivelJugador()).isEqualTo(Nivel.SIN_ESPECIFICAR);

        // registerForDoubleTournament сохраняет возвращённую главную регистрацию повторно
        // (та же логика, что и для уже существующего партнёра) — 2 уникальные записи, 3 save().
        ArgumentCaptor<TournamentRegistration> regCaptor = ArgumentCaptor.forClass(TournamentRegistration.class);
        verify(registrationRepository, times(3)).save(regCaptor.capture());
        List<TournamentRegistration> saved = regCaptor.getAllValues();

        // Обе регистрации (основного игрока и партнёра) сразу CONFIRMED — без ожидания
        assertThat(saved).allSatisfy(reg -> {
            assertThat(reg.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
            assertThat(reg.getPartnerRegistrationToken()).isNull();
        });

        TournamentRegistration mainRegistration = saved.stream()
                .filter(reg -> Long.valueOf(MAIN_PLAYER_ID).equals(reg.getPlayer().getId()))
                .findFirst().orElseThrow();
        assertThat(mainRegistration.getPartnerTelegram()).isEqualTo("pareja_tg");
    }

    @Test
    void parejaConCompaneroSinCuenta_sinSlots_quedaEnListaDeEsperaDeInmediato() {
        when(registrationRepository.countConfirmedPairs(TOURNAMENT_ID)).thenReturn(16L); // cupoMax=16, lleno
        when(registrationRepository.findMaxWaitlistPosition(TOURNAMENT_ID)).thenReturn(Optional.of(0));
        when(playerRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PartnerRegistrationDto partnerDto = new PartnerRegistrationDto();
        partnerDto.setNombre("Pareja");
        partnerDto.setApellido("Nueva");
        partnerDto.setTelefono("+5492222222222");

        service.registerForDoubleTournament(tournamentDto, MAIN_PLAYER_ID, partnerDto);

        ArgumentCaptor<TournamentRegistration> regCaptor = ArgumentCaptor.forClass(TournamentRegistration.class);
        verify(registrationRepository, times(3)).save(regCaptor.capture());

        assertThat(regCaptor.getAllValues())
                .allSatisfy(reg -> assertThat(reg.getStatus()).isEqualTo(RegistrationStatus.WAITLIST));
    }

    @Test
    void telefonoYaRegistrado_lanzaExcepcionSinCrearCuenta() {
        when(playerRepository.existsByTelefono("+5492222222222")).thenReturn(true);

        PartnerRegistrationDto partnerDto = new PartnerRegistrationDto();
        partnerDto.setNombre("Pareja");
        partnerDto.setApellido("Nueva");
        partnerDto.setTelefono("+5492222222222");

        org.junit.jupiter.api.Assertions.assertThrows(
                com.padle.core.padelcoreservice.exception.TournamentRegistrationException.class,
                () -> service.registerForDoubleTournament(tournamentDto, MAIN_PLAYER_ID, partnerDto));

        verify(playerRepository, org.mockito.Mockito.never()).save(any());
    }
}
