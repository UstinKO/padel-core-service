package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.KingOfCourtPlayerStats;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.americano.AmericanoPlayer;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.KingOfCourtPlayerStatsRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoPlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LFPT-443: логика опоздания (штраф −10) для индивидуальных турниров (AMERICANO/KING_OF_COURT).
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
class TournamentServiceLateArrivalTest {

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
    @Autowired
    private AmericanoPlayerRepository americanoPlayerRepository;
    @Autowired
    private TournamentKingOfCourtRepository tournamentKingOfCourtRepository;
    @Autowired
    private KingOfCourtPlayerStatsRepository kingOfCourtPlayerStatsRepository;

    @Test
    void americano_marcarOpoздал_aplicaMenos10YEsIdempotente() {
        Tournament tournament = createTournament(TournamentType.AMERICANO);
        PlayerPadel player = createPlayer();
        createConfirmedRegistration(tournament, player);
        AmericanoPlayer ap = createAmericanoPlayer(tournament, player, 86);

        tournamentService.setLateArrival(tournament.getId(), player.getId(), true);

        AmericanoPlayer reloaded = americanoPlayerRepository.findById(ap.getId()).orElseThrow();
        assertThat(reloaded.getTotalScore()).isEqualTo(76);
        TournamentRegistration reg = registrationRepository
                .findByTournamentIdAndPlayerId(tournament.getId(), player.getId()).orElseThrow();
        assertThat(reg.getLateArrival()).isTrue();

        // Повторный вызов с тем же значением — идемпотентно, штраф не применяется дважды
        tournamentService.setLateArrival(tournament.getId(), player.getId(), true);
        reloaded = americanoPlayerRepository.findById(ap.getId()).orElseThrow();
        assertThat(reloaded.getTotalScore()).isEqualTo(76);
    }

    @Test
    void americano_desmarcarOpoздал_devuelveLos10Puntos() {
        Tournament tournament = createTournament(TournamentType.AMERICANO);
        PlayerPadel player = createPlayer();
        createConfirmedRegistration(tournament, player);
        AmericanoPlayer ap = createAmericanoPlayer(tournament, player, 86);

        tournamentService.setLateArrival(tournament.getId(), player.getId(), true);
        tournamentService.setLateArrival(tournament.getId(), player.getId(), false);

        AmericanoPlayer reloaded = americanoPlayerRepository.findById(ap.getId()).orElseThrow();
        assertThat(reloaded.getTotalScore()).isEqualTo(86);
        TournamentRegistration reg = registrationRepository
                .findByTournamentIdAndPlayerId(tournament.getId(), player.getId()).orElseThrow();
        assertThat(reg.getLateArrival()).isFalse();
    }

    @Test
    void americano_puntosPosterioresSeSumanEncimaDelPenalti() {
        Tournament tournament = createTournament(TournamentType.AMERICANO);
        PlayerPadel player = createPlayer();
        createConfirmedRegistration(tournament, player);
        AmericanoPlayer ap = createAmericanoPlayer(tournament, player, 0);

        tournamentService.setLateArrival(tournament.getId(), player.getId(), true);
        AmericanoPlayer reloaded = americanoPlayerRepository.findById(ap.getId()).orElseThrow();
        reloaded.addMatchResult(32, 20);
        americanoPlayerRepository.save(reloaded);

        AmericanoPlayer afterMatch = americanoPlayerRepository.findById(ap.getId()).orElseThrow();
        assertThat(afterMatch.getTotalScore()).isEqualTo(-10 + 32);
    }

    @Test
    void kingOfCourt_marcarOpoздал_aplicaMenos10() {
        Tournament tournament = createTournament(TournamentType.KING_OF_COURT);
        PlayerPadel player = createPlayer();
        createConfirmedRegistration(tournament, player);
        KingOfCourtPlayerStats stats = createKingOfCourtStats(tournament, player, 50);

        tournamentService.setLateArrival(tournament.getId(), player.getId(), true);

        KingOfCourtPlayerStats reloaded = kingOfCourtPlayerStatsRepository.findById(stats.getId()).orElseThrow();
        assertThat(reloaded.getTotalPoints()).isEqualTo(40);
    }

    @Test
    void canchaAbierta_noPermiteMarcarOpoздал() {
        Tournament tournament = createTournament(TournamentType.CANCHA_ABIERTA);
        PlayerPadel player = createPlayer();
        createConfirmedRegistration(tournament, player);

        assertThatThrownBy(() -> tournamentService.setLateArrival(tournament.getId(), player.getId(), true))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void americanoTeams_noPermiteMarcarOpoздал() {
        Tournament tournament = createTournament(TournamentType.AMERICANO_TEAMS);
        PlayerPadel player = createPlayer();
        createConfirmedRegistration(tournament, player);

        assertThatThrownBy(() -> tournamentService.setLateArrival(tournament.getId(), player.getId(), true))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void kingOfCourt_marcarOpoздал_esIdempotenteYReversible() {
        Tournament tournament = createTournament(TournamentType.KING_OF_COURT);
        PlayerPadel player = createPlayer();
        createConfirmedRegistration(tournament, player);
        KingOfCourtPlayerStats stats = createKingOfCourtStats(tournament, player, 50);

        tournamentService.setLateArrival(tournament.getId(), player.getId(), true);
        tournamentService.setLateArrival(tournament.getId(), player.getId(), true); // repetido — idempotente

        KingOfCourtPlayerStats reloaded = kingOfCourtPlayerStatsRepository.findById(stats.getId()).orElseThrow();
        assertThat(reloaded.getTotalPoints()).isEqualTo(40);

        tournamentService.setLateArrival(tournament.getId(), player.getId(), false);
        reloaded = kingOfCourtPlayerStatsRepository.findById(stats.getId()).orElseThrow();
        assertThat(reloaded.getTotalPoints()).isEqualTo(50);
    }

    private Club createClub() {
        return clubRepository.save(Club.builder()
                .nombre("Club " + UUID.randomUUID())
                .direccion("Av. Test 123")
                .isActive(true)
                .build());
    }

    private PlayerPadel createPlayer() {
        String suffix = "LFPT443-" + UUID.randomUUID();
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

    private Tournament createTournament(TournamentType tipo) {
        Club club = createClub();
        String suffix = "LFPT443-" + UUID.randomUUID();
        Modalidad modalidad = tipo == TournamentType.AMERICANO_TEAMS ? Modalidad.DOBLES : Modalidad.INDIVIDUAL;
        return tournamentRepository.save(Tournament.builder()
                .clubId(club.getId())
                .nombre("Torneo " + suffix)
                .fechaInicio(LocalDateTime.now().plusDays(1).toLocalDate())
                .horaInicio(LocalDateTime.now().plusDays(1).toLocalTime())
                .generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7)
                .tipo(tipo)
                .modalidad(modalidad)
                .cupoMax(8)
                .precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("test@example.com")
                .isActive(true)
                .build());
    }

    private TournamentRegistration createConfirmedRegistration(Tournament tournament, PlayerPadel player) {
        return registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament)
                .player(player)
                .status(RegistrationStatus.CONFIRMED)
                .build());
    }

    private AmericanoPlayer createAmericanoPlayer(Tournament tournament, PlayerPadel player, int totalScore) {
        return americanoPlayerRepository.save(AmericanoPlayer.builder()
                .tournament(tournament)
                .player(player)
                .initialPosition(1)
                .totalScore(totalScore)
                .build());
    }

    private KingOfCourtPlayerStats createKingOfCourtStats(Tournament tournament, PlayerPadel player, int totalPoints) {
        TournamentKingOfCourt king = new TournamentKingOfCourt();
        king.setTournament(tournament);
        king.setIsActive(true);
        king = tournamentKingOfCourtRepository.save(king);

        KingOfCourtPlayerStats stats = new KingOfCourtPlayerStats();
        stats.setTournamentKing(king);
        stats.setPlayer(player);
        stats.setTotalPoints(totalPoints);
        return kingOfCourtPlayerStatsRepository.save(stats);
    }
}
