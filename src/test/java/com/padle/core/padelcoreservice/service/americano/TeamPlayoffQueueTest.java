package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffTeamRequest;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LFPT-367: cola de espera para parejas de calificación sin cancha asignada — un partido puede
 * crearse antes de que haya una cancha libre ({@code QUEUED}), ambos equipos quedan ocupados
 * (igual que si ya estuvieran jugando), y el coordinador asigna la cancha después, uno a la vez.
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
class TeamPlayoffQueueTest {

    @Autowired
    private TeamPlayoffService playoffService;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private AmericanoMatchRepository matchRepository;

    @Test
    void queueQualificationMatch_createsQueuedMatchWithoutCourt_andBlocksBothTeams() {
        Long tournamentId = createTournament();
        AmericanoTeam t1 = createTeam(tournamentId);
        AmericanoTeam t2 = createTeam(tournamentId);
        AmericanoTeam t3 = createTeam(tournamentId);

        AmericanoMatch queued = playoffService.queueQualificationMatch(tournamentId, t1.getId(), t2.getId());

        assertThat(queued.getCourtNumber()).isNull();
        assertThat(queued.getStatus()).isEqualTo(AmericanoRoundStatus.QUEUED);
        assertThat(queued.isQueued()).isTrue();
        assertThat(queued.getStartedAt()).isNull();

        List<Long> availableIds = playoffService.getAvailableTeamsForQualification(tournamentId).stream()
                .map(AmericanoTeamDto::getId).toList();
        assertThat(availableIds).doesNotContain(t1.getId(), t2.getId());
        assertThat(availableIds).contains(t3.getId());
    }

    @Test
    void queueQualificationMatch_rejectsTeamAlreadyQueuedElsewhere() {
        Long tournamentId = createTournament();
        AmericanoTeam t1 = createTeam(tournamentId);
        AmericanoTeam t2 = createTeam(tournamentId);
        AmericanoTeam t3 = createTeam(tournamentId);

        playoffService.queueQualificationMatch(tournamentId, t1.getId(), t2.getId());

        assertThatThrownBy(() -> playoffService.queueQualificationMatch(tournamentId, t1.getId(), t3.getId()))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void getQueue_ordersByWaitTime_oldestFirst() throws InterruptedException {
        Long tournamentId = createTournament();
        AmericanoTeam t1 = createTeam(tournamentId);
        AmericanoTeam t2 = createTeam(tournamentId);
        AmericanoTeam t3 = createTeam(tournamentId);
        AmericanoTeam t4 = createTeam(tournamentId);

        AmericanoMatch first = playoffService.queueQualificationMatch(tournamentId, t1.getId(), t2.getId());
        Thread.sleep(20);
        AmericanoMatch second = playoffService.queueQualificationMatch(tournamentId, t3.getId(), t4.getId());

        List<AmericanoMatchDto> queue = playoffService.getQueue(tournamentId);

        assertThat(queue).hasSize(2);
        assertThat(queue.get(0).getId()).isEqualTo(first.getId());
        assertThat(queue.get(0).getQueuePosition()).isEqualTo(1);
        assertThat(queue.get(1).getId()).isEqualTo(second.getId());
        assertThat(queue.get(1).getQueuePosition()).isEqualTo(2);
        assertThat(queue.get(0).getWaitingMinutes()).isNotNegative();
    }

    @Test
    void assignCourtToQueuedMatch_movesToInProgressAndOccupiesCourt() {
        Long tournamentId = createTournament();
        AmericanoTeam t1 = createTeam(tournamentId);
        AmericanoTeam t2 = createTeam(tournamentId);
        AmericanoTeam t3 = createTeam(tournamentId);
        AmericanoTeam t4 = createTeam(tournamentId);

        AmericanoMatch queued = playoffService.queueQualificationMatch(tournamentId, t1.getId(), t2.getId());

        AmericanoMatch assigned = playoffService.assignCourtToQueuedMatch(queued.getId(), 3);

        assertThat(assigned.getCourtNumber()).isEqualTo(3);
        assertThat(assigned.isInProgress()).isTrue();
        assertThat(assigned.getStartedAt()).isNotNull();
        assertThat(playoffService.getQueue(tournamentId)).isEmpty();

        // Корт 3 теперь занят этим матчем — обычное создание нового матча на том же корте должно упасть.
        assertThatThrownBy(() -> playoffService.createQualificationMatch(tournamentId, t3.getId(), t4.getId(), 3))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void assignCourtToQueuedMatch_rejectsOccupiedCourt() {
        Long tournamentId = createTournament();
        AmericanoTeam t1 = createTeam(tournamentId);
        AmericanoTeam t2 = createTeam(tournamentId);
        AmericanoTeam t3 = createTeam(tournamentId);
        AmericanoTeam t4 = createTeam(tournamentId);

        playoffService.createQualificationMatch(tournamentId, t1.getId(), t2.getId(), 1);
        AmericanoMatch queued = playoffService.queueQualificationMatch(tournamentId, t3.getId(), t4.getId());

        assertThatThrownBy(() -> playoffService.assignCourtToQueuedMatch(queued.getId(), 1))
                .isInstanceOf(InvalidStateException.class);

        AmericanoMatch stillQueued = matchRepository.findById(queued.getId()).orElseThrow();
        assertThat(stillQueued.isQueued()).isTrue();
        assertThat(stillQueued.getCourtNumber()).isNull();
    }

    @Test
    void assignCourtToQueuedMatch_rejectsWhenMatchNotQueued() {
        Long tournamentId = createTournament();
        AmericanoTeam t1 = createTeam(tournamentId);
        AmericanoTeam t2 = createTeam(tournamentId);

        AmericanoMatch inProgress = playoffService.createQualificationMatch(tournamentId, t1.getId(), t2.getId(), 1);

        assertThatThrownBy(() -> playoffService.assignCourtToQueuedMatch(inProgress.getId(), 2))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void removeFromQueue_deletesMatchAndFreesBothTeams() {
        Long tournamentId = createTournament();
        AmericanoTeam t1 = createTeam(tournamentId);
        AmericanoTeam t2 = createTeam(tournamentId);

        AmericanoMatch queued = playoffService.queueQualificationMatch(tournamentId, t1.getId(), t2.getId());

        playoffService.removeFromQueue(queued.getId());

        assertThat(matchRepository.findById(queued.getId())).isEmpty();
        List<Long> availableIds = playoffService.getAvailableTeamsForQualification(tournamentId).stream()
                .map(AmericanoTeamDto::getId).toList();
        assertThat(availableIds).contains(t1.getId(), t2.getId());
    }

    @Test
    void removeFromQueue_rejectsWhenMatchNotQueued() {
        Long tournamentId = createTournament();
        AmericanoTeam t1 = createTeam(tournamentId);
        AmericanoTeam t2 = createTeam(tournamentId);

        AmericanoMatch inProgress = playoffService.createQualificationMatch(tournamentId, t1.getId(), t2.getId(), 1);

        assertThatThrownBy(() -> playoffService.removeFromQueue(inProgress.getId()))
                .isInstanceOf(InvalidStateException.class);
        assertThat(matchRepository.findById(inProgress.getId())).isPresent();
    }

    @Test
    void changeMatchTeam_onQueuedMatch_keepsItQueuedAndSwapsAvailability() {
        Long tournamentId = createTournament();
        AmericanoTeam t1 = createTeam(tournamentId);
        AmericanoTeam t2 = createTeam(tournamentId);
        AmericanoTeam t3 = createTeam(tournamentId);

        AmericanoMatch queued = playoffService.queueQualificationMatch(tournamentId, t1.getId(), t2.getId());

        TeamPlayoffService.MatchTeamChangeResult result =
                playoffService.changeMatchTeam(queued.getId(), 1, t3.getId());

        assertThat(result.match().isQueued()).isTrue();
        assertThat(result.match().getTeam1Id()).isEqualTo(t3.getId());

        List<Long> availableIds = playoffService.getAvailableTeamsForQualification(tournamentId).stream()
                .map(AmericanoTeamDto::getId).toList();
        assertThat(availableIds).contains(t1.getId());
        assertThat(availableIds).doesNotContain(t2.getId(), t3.getId());
    }

    @Test
    void getQualRanking_exposesQueuedStatusWithPositionAndOpponent() {
        Long tournamentId = createTournament();
        AmericanoTeam t1 = createTeam(tournamentId);
        AmericanoTeam t2 = createTeam(tournamentId);

        playoffService.queueQualificationMatch(tournamentId, t1.getId(), t2.getId());

        List<AmericanoTeamDto> ranking = playoffService.getQualRanking(tournamentId).getRanking();
        AmericanoTeamDto dto1 = ranking.stream().filter(t -> t.getId().equals(t1.getId())).findFirst().orElseThrow();
        AmericanoTeamDto dto2 = ranking.stream().filter(t -> t.getId().equals(t2.getId())).findFirst().orElseThrow();

        assertThat(dto1.getTournamentStatus()).isEqualTo("QUEUED");
        assertThat(dto1.getQueuePosition()).isEqualTo(1);
        assertThat(dto1.getQueueOpponentName()).isEqualTo(t2.getDisplayName());

        assertThat(dto2.getTournamentStatus()).isEqualTo("QUEUED");
        assertThat(dto2.getQueuePosition()).isEqualTo(1);
        assertThat(dto2.getQueueOpponentName()).isEqualTo(t1.getDisplayName());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SETUP
    // ═══════════════════════════════════════════════════════════════════════

    private Long createTournament() {
        String suffix = "LFPT367-" + UUID.randomUUID();
        Club club = clubRepository.save(Club.builder().nombre("Club " + suffix).isActive(true).build());
        Tournament tournament = tournamentRepository.save(Tournament.builder()
                .clubId(club.getId())
                .nombre("Torneo " + suffix)
                .fechaInicio(LocalDate.now().plusDays(7))
                .horaInicio(LocalTime.of(10, 0))
                .generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7)
                .tipo(TournamentType.AMERICANO_TEAMS)
                .modalidad(Modalidad.DOBLES)
                .cupoMax(32)
                .precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("test@example.com")
                .isActive(true)
                .build());
        return tournament.getId();
    }

    private AmericanoTeam createTeam(Long tournamentId) {
        PlayerPadel p1 = createPlayer(tournamentId);
        PlayerPadel p2 = createPlayer(tournamentId);

        TeamPlayoffTeamRequest req = new TeamPlayoffTeamRequest();
        req.setPlayer1Id(p1.getId());
        req.setPlayer2Id(p2.getId());
        req.setRegistrationSource("ADMIN_MANUAL");
        req.setHasPaid(true);
        req.setAttended(true);

        return playoffService.addTeam(tournamentId, req);
    }

    private PlayerPadel createPlayer(Long tournamentId) {
        String suffix = tournamentId + "-" + UUID.randomUUID();
        return playerRepository.save(PlayerPadel.builder()
                .nombre("LFPT367")
                .apellido("Player" + suffix)
                .email("lfpt367-" + suffix + "@example.com")
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }
}
