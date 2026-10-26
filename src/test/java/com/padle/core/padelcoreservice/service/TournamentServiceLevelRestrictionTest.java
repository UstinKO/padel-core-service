package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.exception.PlayerLevelRequiredException;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.mapper.TournamentMapper;
import com.padle.core.padelcoreservice.mapper.TournamentRegistrationMapper;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.KingOfCourtPlayerStatsRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoPlayerRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Проверка уровня в {@link TournamentService#registerPlayer}. Чистая логика покрыта
 * в {@link TournamentLevelPolicyTest}; здесь проверяется только, что сервис вызывает её
 * в правильной точке (до изменения состояния регистрации) и переводит вердикт
 * в ожидаемое исключение.
 */
@ExtendWith(MockitoExtension.class)
class TournamentServiceLevelRestrictionTest {

    private static final long TOURNAMENT_ID = 1L;
    private static final long PLAYER_ID = 2L;

    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private TournamentMapper tournamentMapper;
    @Mock private TournamentRegistrationMapper registrationMapper;
    @Mock private ClubService clubService;
    @Mock private PlayerRepository playerRepository;
    @Mock private TournamentKingOfCourtRepository tournamentKingOfCourtRepository;
    @Mock private EmailService emailService;
    @Mock private TournamentAccessService tournamentAccessService;
    @Mock private AmericanoPlayerRepository americanoPlayerRepository;
    @Mock private AmericanoRoundRepository americanoRoundRepository;
    @Mock private AmericanoMatchRepository americanoMatchRepository;
    @Mock private AmericanoTeamRepository americanoTeamRepository;
    @Mock private KingOfCourtPlayerStatsRepository kingOfCourtPlayerStatsRepository;
    @Mock private MessageSource messageSource;

    @InjectMocks private TournamentService tournamentService;

    private Tournament tournament;
    private PlayerPadel player;

    @BeforeEach
    void setUp() {
        tournament = Tournament.builder()
                .id(TOURNAMENT_ID)
                .modalidad(Modalidad.INDIVIDUAL)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .tipo(TournamentType.AMERICANO)
                .categoriaNivel(Nivel.C6)
                .fechaInicio(LocalDate.now().plusDays(7))
                .cupoMax(32)
                .isActive(true)
                .horaInicio(LocalTime.of(18, 0))
                .build();

        player = PlayerPadel.builder()
                .id(PLAYER_ID)
                .telefono("+5491111111111")
                .nivelJugador(Nivel.C8)
                .build();

        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament));
        when(playerRepository.findById(PLAYER_ID)).thenReturn(Optional.of(player));
    }

    @Test
    void jugadorFueraDeRango_bloqueaAntesDeTocarLaRegistracion() {
        // C8 vs C6 (torneo) difieren en 2 categorías — fuera del rango ±1 (ejemplo del cliente)
        when(messageSource.getMessage(eq("tournament.register.level_mismatch"), any(), any(Locale.class)))
                .thenReturn("Tu nivel actual es C8. El registro al torneo de categoría C6 no está disponible.");

        assertThatThrownBy(() -> tournamentService.registerPlayer(TOURNAMENT_ID, PLAYER_ID))
                .isInstanceOf(TournamentRegistrationException.class)
                .isNotInstanceOf(PlayerLevelRequiredException.class)
                .hasMessageContaining("C8")
                .hasMessageContaining("C6");

        // El chequeo de nivel ocurre antes de leer/escribir la inscripción existente
        Mockito.verifyNoInteractions(registrationRepository);
    }

    @Test
    void jugadorSinNivelEspecificado_bloqueaConExcepcionDistinguible() {
        player.setNivelJugador(Nivel.SIN_ESPECIFICAR);
        when(messageSource.getMessage(eq("tournament.register.level_required"), any(), any(Locale.class)))
                .thenReturn("Para inscribirte en este torneo primero tenés que indicar tu nivel en tu perfil.");

        assertThatThrownBy(() -> tournamentService.registerPlayer(TOURNAMENT_ID, PLAYER_ID))
                .isInstanceOf(PlayerLevelRequiredException.class);

        Mockito.verifyNoInteractions(registrationRepository);
    }

    @Test
    void jugadorDentroDeUnaCategoria_seRegistraNormalmente() {
        tournament.setCategoriaNivel(Nivel.C7); // C8 vs C7 — una categoría de diferencia, permitido
        stubSuccessfulRegistration();

        assertThat(tournamentService.registerPlayer(TOURNAMENT_ID, PLAYER_ID)).isNotNull();
    }

    @Test
    void torneoDeFormatoAbierto_noAplicaElChequeoAunqueElNivelEsteLejos() {
        tournament.setTipo(TournamentType.CANCHA_ABIERTA);
        tournament.setCategoriaNivel(Nivel.C6); // seguiría siendo "lejos" de C8, pero CANCHA_ABIERTA está exenta
        stubSuccessfulRegistration();

        assertThat(tournamentService.registerPlayer(TOURNAMENT_ID, PLAYER_ID)).isNotNull();
    }

    private void stubSuccessfulRegistration() {
        when(registrationRepository.findByTournamentIdAndPlayerId(TOURNAMENT_ID, PLAYER_ID))
                .thenReturn(Optional.empty());
        // Modalidad.INDIVIDUAL (ver setUp) — registerPlayer cuenta plazas ocupadas con
        // countActiveRegistrations, no countUniquePairs (esa es la rama DOBLES).
        when(registrationRepository.countActiveRegistrations(TOURNAMENT_ID)).thenReturn(0L);
        when(registrationRepository.countByTournamentIdAndStatus(anyLong(), any())).thenReturn(0L);
        when(registrationRepository.save(any(TournamentRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationMapper.toDto(any(TournamentRegistration.class)))
                .thenReturn(new TournamentRegistrationDto());
    }
}
