package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-0637: «Мои мероприятия» для клиента 1Padel.App — read-only обзор собственных
 * регистраций игрока (то же разбиение турниров по {@link TournamentStatus}, что и в
 * дашборде игрока, см. {@link TournamentService#getHistoricalAndActiveTournamentsByPlayer}).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key",
})
@Transactional
class TournamentServiceMyTournamentsTest {

    @Autowired
    private TournamentService tournamentService;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private TournamentRegistrationRepository registrationRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void jugadorSinRegistros_recibeListasVacias() {
        PlayerPadel player = createPlayer();

        TournamentService.PlayerTournamentsOverview overview =
                tournamentService.getMyTournamentsOverview(player.getId());

        assertThat(overview.upcoming()).isEmpty();
        assertThat(overview.historical()).isEmpty();
    }

    @Test
    void registroEnTorneoAbierto_apareceEnUpcomingConEstadoYListaDeEspera() {
        PlayerPadel player = createPlayer();
        Tournament tournament = createTournament(TournamentStatus.REGISTRO_ABIERTO);
        createRegistration(tournament, player, RegistrationStatus.WAITLIST, 3);

        TournamentService.PlayerTournamentsOverview overview =
                tournamentService.getMyTournamentsOverview(player.getId());

        assertThat(overview.historical()).isEmpty();
        assertThat(overview.upcoming()).hasSize(1);
        TournamentService.MyTournamentEntry entry = overview.upcoming().get(0);
        assertThat(entry.tournament().getId()).isEqualTo(tournament.getId());
        assertThat(entry.registrationStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(entry.waitlistPosition()).isEqualTo(3);
    }

    @Test
    void registroEnTorneoFinalizado_apareceSoloEnHistorial() {
        PlayerPadel player = createPlayer();
        Tournament tournament = createTournament(TournamentStatus.FINALIZADO);
        createRegistration(tournament, player, RegistrationStatus.CONFIRMED, null);

        TournamentService.PlayerTournamentsOverview overview =
                tournamentService.getMyTournamentsOverview(player.getId());

        assertThat(overview.upcoming()).isEmpty();
        assertThat(overview.historical()).hasSize(1);
        assertThat(overview.historical().get(0).getId()).isEqualTo(tournament.getId());
    }

    @Test
    void registroCancelado_noApareceNiEnUpcomingNiEnHistorial() {
        PlayerPadel player = createPlayer();
        Tournament tournament = createTournament(TournamentStatus.REGISTRO_ABIERTO);
        TournamentRegistration registration = createRegistration(tournament, player, RegistrationStatus.CONFIRMED, null);
        registration.cancel("prueba");
        registrationRepository.save(registration);

        TournamentService.PlayerTournamentsOverview overview =
                tournamentService.getMyTournamentsOverview(player.getId());

        assertThat(overview.upcoming()).isEmpty();
        assertThat(overview.historical()).isEmpty();
    }

    private Club createClub() {
        return clubRepository.save(Club.builder()
                .nombre("Club " + UUID.randomUUID())
                .direccion("Av. Test 123")
                .isActive(true)
                .build());
    }

    private PlayerPadel createPlayer() {
        String suffix = "LFPT637-" + UUID.randomUUID();
        return playerRepository.save(PlayerPadel.builder()
                .nombre("Test")
                .apellido("Player")
                .email(suffix + "@example.com")
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }

    private Tournament createTournament(TournamentStatus estado) {
        Club club = createClub();
        String suffix = "LFPT637-" + UUID.randomUUID();
        return tournamentRepository.save(Tournament.builder()
                .clubId(club.getId())
                .nombre("Torneo " + suffix)
                .fechaInicio(LocalDateTime.now().plusDays(1).toLocalDate())
                .horaInicio(LocalDateTime.now().plusDays(1).toLocalTime())
                .generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7)
                .tipo(TournamentType.AMERICANO)
                .modalidad(Modalidad.INDIVIDUAL)
                .cupoMax(8)
                .precio(BigDecimal.ZERO)
                .estado(estado)
                .contactoOrganizador("test@example.com")
                .isActive(true)
                .build());
    }

    private TournamentRegistration createRegistration(Tournament tournament, PlayerPadel player,
                                                        RegistrationStatus status, Integer waitlistPosition) {
        return registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament)
                .player(player)
                .status(status)
                .waitlistPosition(waitlistPosition)
                .build());
    }
}
