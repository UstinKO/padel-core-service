package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.MatchDto;
import com.padle.core.padelcoreservice.dto.RankingDto;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Match;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Ranking;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.MatchStatus;
import com.padle.core.padelcoreservice.model.enums.MatchType;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.MatchRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.RankingRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * LFPT-398: registrarVictoria/registrarDerrota/inicializarRanking no deben fallar
 * para un jugador que todavía no tiene fila en ranking_db (caso estándar de un
 * jugador nuevo/poco activo que recién recibe un resultado de partido).
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
class RankingServiceTest {

    @Autowired
    private RankingService rankingService;
    @Autowired
    private MatchService matchService;
    @Autowired
    private RankingRepository rankingRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private ClubRepository clubRepository;

    @Test
    void registrarVictoria_jugadorSinRankingPrevio_creaRankingConRachasEnCero() {
        PlayerPadel player = createPlayer();
        Match match = createMatch();

        assertThatCode(() -> rankingService.registrarVictoria(player.getId(), match))
                .doesNotThrowAnyException();

        Ranking ranking = rankingRepository.findByPlayerId(player.getId()).orElseThrow();
        assertThat(ranking.getRachasActual()).isZero();
        assertThat(ranking.getRachasMaxima()).isZero();
        assertThat(ranking.getPartidosGanados()).isEqualTo(1);
    }

    @Test
    void registrarDerrota_jugadorSinRankingPrevio_creaRankingConRachasEnCero() {
        PlayerPadel player = createPlayer();
        Match match = createMatch();

        assertThatCode(() -> rankingService.registrarDerrota(player.getId(), match))
                .doesNotThrowAnyException();

        Ranking ranking = rankingRepository.findByPlayerId(player.getId()).orElseThrow();
        assertThat(ranking.getRachasActual()).isZero();
        assertThat(ranking.getRachasMaxima()).isZero();
        assertThat(ranking.getPartidosPerdidos()).isEqualTo(1);
    }

    @Test
    void inicializarRanking_jugadorSinRankingPrevio_creaRankingConRachasEnCero() {
        PlayerPadel player = createPlayer();

        RankingDto dto = rankingService.inicializarRanking(player.getId());

        assertThat(dto).isNotNull();
        Ranking ranking = rankingRepository.findByPlayerId(player.getId()).orElseThrow();
        assertThat(ranking.getRachasActual()).isZero();
        assertThat(ranking.getRachasMaxima()).isZero();
    }

    @Test
    void updateMatchResult_jugadoresSinRankingPrevio_flujoCompletoNoFalla() {
        // Reproduce el escenario end-to-end del issue: MatchService.updateMatchResult
        // (el mismo camino que POST /admin/tournaments/{id}/matches/{matchId}, LFPT-394)
        // para dos jugadores que todavía no tienen fila en ranking_db.
        PlayerPadel winner = createPlayer();
        PlayerPadel loser = createPlayer();
        Match match = createMatch(winner.getId(), loser.getId());

        MatchDto resultDto = MatchDto.builder()
                .set1Equipo1(6)
                .set1Equipo2(2)
                .set2Equipo1(6)
                .set2Equipo2(3)
                .build();

        assertThatCode(() -> matchService.updateMatchResult(match.getId(), resultDto))
                .doesNotThrowAnyException();

        Match persisted = matchRepository.findById(match.getId()).orElseThrow();
        assertThat(persisted.getEstado()).isEqualTo(MatchStatus.FINALIZADO);
        assertThat(persisted.getGanadorId()).isEqualTo(winner.getId());

        Ranking winnerRanking = rankingRepository.findByPlayerId(winner.getId()).orElseThrow();
        Ranking loserRanking = rankingRepository.findByPlayerId(loser.getId()).orElseThrow();
        assertThat(winnerRanking.getRachasActual()).isZero();
        assertThat(winnerRanking.getRachasMaxima()).isZero();
        assertThat(winnerRanking.getPartidosGanados()).isEqualTo(1);
        assertThat(loserRanking.getRachasActual()).isZero();
        assertThat(loserRanking.getRachasMaxima()).isZero();
        assertThat(loserRanking.getPartidosPerdidos()).isEqualTo(1);
    }

    private PlayerPadel createPlayer() {
        String suffix = "LFPT398-" + UUID.randomUUID();
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

    private Match createMatch() {
        return createMatch(null, null);
    }

    private Match createMatch(Long player1Id, Long player2Id) {
        Club club = clubRepository.save(Club.builder()
                .nombre("Club " + UUID.randomUUID())
                .isActive(true)
                .build());
        Tournament tournament = tournamentRepository.save(Tournament.builder()
                .clubId(club.getId())
                .nombre("Torneo " + UUID.randomUUID())
                .fechaInicio(LocalDate.now().plusDays(7))
                .horaInicio(LocalTime.of(10, 0))
                .generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7)
                .tipo(TournamentType.CANCHA_ABIERTA)
                .modalidad(Modalidad.INDIVIDUAL)
                .cupoMax(8)
                .precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("test@example.com")
                .isActive(true)
                .build());
        return matchRepository.save(Match.builder()
                .tournamentId(tournament.getId())
                .ronda(1)
                .partidoNumero(1)
                .tipo(MatchType.INDIVIDUAL)
                .player1Id(player1Id)
                .player2Id(player2Id)
                .build());
    }
}
