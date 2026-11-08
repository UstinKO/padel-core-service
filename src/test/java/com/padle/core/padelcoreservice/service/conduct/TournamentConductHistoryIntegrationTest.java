package com.padle.core.padelcoreservice.service.conduct;

import com.padle.core.padelcoreservice.dto.CourtDTO;
import com.padle.core.padelcoreservice.dto.KingOfCourtStateDTO;
import com.padle.core.padelcoreservice.dto.PlayerStatsDTO;
import com.padle.core.padelcoreservice.dto.americano.RankingCriteria;
import com.padle.core.padelcoreservice.service.conduct.AdditionalPointsService.Change;
import com.padle.core.padelcoreservice.dto.americano.AmericanoConfigDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchResultDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoConfigDto;
import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffAddTeamRequest;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import com.padle.core.padelcoreservice.service.americano.TeamAmericanoService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key"
})
@Transactional
class TournamentConductHistoryIntegrationTest {

    @Autowired private TournamentConductHistory history;
    @Autowired private AdditionalPointsService points;
    @Autowired private JdbcClient jdbc;
    @Autowired private KingOfCourtService kingOfCourt;
    @Autowired private AmericanoService americano;
    @Autowired private TeamPlayoffService playoff;
    @Autowired private TeamAmericanoService teamAmericano;
    @Autowired private ClubRepository clubs;
    @Autowired private TournamentRepository tournaments;
    @Autowired private PlayerRepository players;
    @Autowired private TournamentRegistrationRepository registrations;
    @Autowired private AmericanoTeamRepository teams;
    @Autowired private EntityManager entityManager;

    private final Owner owner = Owner.builder().id(90001L).firstName("Test").role(OwnerRole.SUPER_ADMIN).build();

    @Test
    void additionalPointsKeepKingMatchAndCourtBonusSeparateAcrossCorrectionsAndRestore() {
        long event = event(TournamentType.KING_OF_COURT, Modalidad.INDIVIDUAL, 4);
        registerFourPlayers(event);
        long king = kingOfCourt.initializeTournament(owner, event, 1, 0).getId();
        long player = kingOfCourt.getCurrentState(king).getCourts().get(0).getTeams().get(0).getPlayers().get(0).getId();
        long other = kingOfCourt.getCurrentState(king).getCourts().get(0).getTeams().get(1).getPlayers().get(0).getId();
        jdbc.sql("""
                UPDATE king_of_court_player_stats_db SET total_points = -10
                WHERE tournament_king_id = :king AND player_id = :player
                """).param("king", king).param("player", player).update();
        entityManager.clear();
        var legacy = kingPlayer(king, player);
        assertThat(legacy.getMatchPoints()).isZero();
        assertThat(legacy.getAdditionalPoints().total()).isEqualTo(-10);
        assertThat(legacy.getTotalPoints()).isEqualTo(-10);
        var legacyEntry = points.editor(event, false, owner).entries().get(0);

        points.add(event, false, new Change(player, 1, 5, "Private note"), owner);
        assertThat(kingPlayer(king, player).getTotalPoints()).isEqualTo(-5);
        long initialAdjustment = version(event, ConductChange.Action.ADD_POINTS);
        var editId = points.editor(event, false, owner).entries().stream()
                .filter(entry -> entry.delta() == 5).findFirst().orElseThrow().id();
        points.edit(event, false, editId, new Change(other, 1, 3, "Corrected"), owner);
        assertThat(kingPlayer(king, player).getAdditionalPoints().total()).isEqualTo(-10);
        assertThat(kingPlayer(king, other).getAdditionalPoints().total()).isEqualTo(3);

        score(king, 12, 9);
        var scored = kingPlayer(king, player);
        assertThat(scored.getMatchPoints()).isEqualTo(12);
        assertThat(scored.getBonusPoints()).isEqualTo(3);
        assertThat(scored.getTotalPoints()).isEqualTo(5);
        kingOfCourt.resetRound(kingOfCourt.getCurrentState(king).getCurrentRoundId());
        assertThat(kingPlayer(king, player).getTotalPoints()).isEqualTo(-10);
        assertThat(kingPlayer(king, other).getAdditionalPoints().total()).isEqualTo(3);

        restore(event, initialAdjustment);
        assertThat(kingPlayer(king, player).getAdditionalPoints().total()).isEqualTo(-5);
        assertThat(kingPlayer(king, other).getAdditionalPoints().total()).isZero();

        jdbc.sql("UPDATE tournament_conduct_history SET snapshot = snapshot - 'tournament_additional_points' WHERE id = :id")
                .param("id", initialAdjustment).update();
        restore(event, initialAdjustment);
        assertThat(kingPlayer(king, player).getAdditionalPoints().total()).isEqualTo(-10);
        assertThat(kingPlayer(king, player).getTotalPoints()).isEqualTo(-10);
        points.add(event, false, new Change(player, 1, 5, ""), owner);
        score(king, 12, 9);
        kingOfCourt.finishTournament(king);
        points.cancel(event, true, legacyEntry.id(), owner);
        assertThat(kingOfCourt.getCurrentState(king).getIsFinished()).isTrue();
        assertThat(kingPlayer(king, player).getTotalPoints()).isEqualTo(20);
        assertThat(points.editor(event, true, owner).history())
                .anySatisfy(entry -> assertThat(entry.action()).isEqualTo("CANCEL_POINTS"));
        assertThatThrownBy(() -> points.editor(event, false, owner)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void americanoUsesAdjustedScoreAtItsExistingPositionInTheRanking() {
        long event = event(TournamentType.AMERICANO, Modalidad.INDIVIDUAL, 4);
        registerFourPlayers(event);
        tournaments.findById(event).orElseThrow().setEstado(TournamentStatus.CERRADO);
        americano.initializeAmericanoTournament(event,
                AmericanoConfigDto.builder().courts(1).totalRounds(2).pointsPerMatch(21).build(), owner);
        var firstRound = americano.getRounds(event).get(0);
        var match = americano.getMatchesByRound(firstRound.getId()).get(0);
        americano.submitMatchResult(result(match.getId(), 12), owner);
        long player = match.getTeam2Player1Id();
        jdbc.sql("UPDATE americano_players_db SET total_score = points_scored - 10 WHERE tournament_id = :id AND player_id = :player")
                .param("id", event).param("player", player).update();
        entityManager.clear();
        points.add(event, false, new Change(player, 1, 20, ""), owner);

        var byScore = americano.getRankingWithDetails(event, RankingCriteria.builder().sortBy("score").build());
        var byWins = americano.getRankingWithDetails(event, RankingCriteria.builder().sortBy("wins").build());
        assertThat(byScore.getRanking().get(0).getPlayerId()).isEqualTo(player);
        assertThat(byScore.getRanking().get(0).getMatchPoints()).isEqualTo(9);
        assertThat(byScore.getRanking().get(0).getTotalScore()).isEqualTo(19);
        assertThat(byScore.getRanking().get(0).getAdditionalPoints().total()).isEqualTo(10);
        assertThat(byWins.getRanking().get(0).getMatchesWon()).isEqualTo(1);
    }

    private PlayerStatsDTO kingPlayer(long king, long player) {
        return kingOfCourt.getRanking(king).stream().filter(entry -> entry.getPlayerId() == player)
                .findFirst().orElseThrow();
    }

    @Test
    void kingResetKeepsEarlierResultsAndCanRestoreSecondRoundAndRestart() {
        long event = event(TournamentType.KING_OF_COURT, Modalidad.INDIVIDUAL, 4);
        registerFourPlayers(event);
        long king = kingOfCourt.initializeTournament(owner, event, 1, 1).getId();
        assertThat(kingOfCourt.initializeTournament(owner, event, 1, 1).getId()).isEqualTo(king);

        score(king, 12, 9);
        KingOfCourtStateDTO first = kingOfCourt.getCurrentState(king);
        kingOfCourt.nextRound(king);
        score(king, 14, 7);
        score(king, 14, 7);
        KingOfCourtStateDTO second = kingOfCourt.getCurrentState(king);

        kingOfCourt.resetRound(second.getCurrentRoundId());
        entityManager.clear();
        KingOfCourtStateDTO reset = kingOfCourt.getCurrentState(king);
        assertThat(reset.getCurrentRoundId()).isEqualTo(second.getCurrentRoundId());
        assertThat(reset.getHistory()).isEqualTo(first.getHistory());
        assertThat(reset.getRanking()).usingRecursiveComparison()
                .ignoringFields("rank").ignoringCollectionOrder().isEqualTo(first.getRanking());
        assertThat(reset.getCourts().get(0).getTeams()).isEqualTo(second.getCourts().get(0).getTeams());
        assertThat(reset.getCourts().get(0).getResult()).isNull();

        restore(event, version(event, ConductChange.Action.CHECKPOINT_RESET_ROUND));
        assertThat(kingOfCourt.getCurrentState(king)).usingRecursiveComparison()
                .isEqualTo(second);
        kingOfCourt.finishTournament(king);
        kingOfCourt.resetTournament(king);
        long replacement = kingOfCourt.initializeTournament(owner, event, 1, 1).getId();
        assertThat(replacement).isNotEqualTo(king);
        restore(event, version(event, ConductChange.Action.CHECKPOINT_RESTART));
        assertThat(kingOfCourt.getCurrentState(king).getIsFinished()).isTrue();
        assertThat(kingOfCourt.getCurrentState(king).getHistory()).isEqualTo(second.getHistory());
        assertThat(history.list(event, owner)).extracting(TournamentConductHistory.Version::action)
                .contains(ConductChange.Action.RESET_ROUND, ConductChange.Action.CHECKPOINT_RESTORE,
                        ConductChange.Action.FINISH, ConductChange.Action.RESTART, ConductChange.Action.RESTORE);
    }

    @Test
    void individualAmericanoRestoresTheRoundScoreAndStandingsTogether() {
        long event = event(TournamentType.AMERICANO, Modalidad.INDIVIDUAL, 4);
        registerFourPlayers(event);
        tournaments.findById(event).orElseThrow().setEstado(TournamentStatus.CERRADO);
        americano.initializeAmericanoTournament(event,
                AmericanoConfigDto.builder().courts(1).totalRounds(2).pointsPerMatch(21).build(), owner);
        var round = americano.getRounds(event).get(0);
        long match = americano.getMatchesByRound(round.getId()).get(0).getId();
        americano.submitMatchResult(result(match, 12), owner);
        long original = version(event, ConductChange.Action.RESULT);
        var originalRanking = americano.getRanking(event, "totalScore", "desc");
        americano.submitMatchResult(result(match, 16), owner);
        assertThat(americano.getMatch(match).getTeam1Score()).isEqualTo(16);

        restore(event, original);
        assertThat(americano.getMatch(match).getTeam1Score()).isEqualTo(12);
        assertThat(americano.getRanking(event, "totalScore", "desc")).usingRecursiveComparison()
                .ignoringCollectionOrder().isEqualTo(originalRanking);
    }

    @Test
    void roundRobinAmericanoRestoresThePairsAndResult() {
        long eventId = event(TournamentType.AMERICANO, Modalidad.DOBLES, 4);
        Tournament event = tournaments.findById(eventId).orElseThrow();
        event.setEstado(TournamentStatus.CERRADO);
        for (int i = 0; i < 2; i++) {
            PlayerPadel first = player();
            PlayerPadel second = player();
            for (PlayerPadel player : List.of(first, second)) {
                registrations.save(TournamentRegistration.builder().tournament(event).player(player)
                        .status(RegistrationStatus.CONFIRMED).isDoubleRegistration(true)
                        .mainPlayerId(first.getId()).position(i + 1).build());
            }
        }
        var config = new TeamAmericanoConfigDto();
        config.setCourts(1);
        config.setPointsPerMatch(21);
        teamAmericano.initialize(eventId, config);
        var first = americano.getMatches(eventId).get(0);
        teamAmericano.submitMatchResult(first.getId(), 13, 8);
        long original = version(eventId, ConductChange.Action.RESULT);
        teamAmericano.submitMatchResult(first.getId(), 16, 5);

        restore(eventId, original);
        assertThat(americano.getMatches(eventId)).singleElement().satisfies(match -> {
            assertThat(match.getId()).isEqualTo(first.getId());
            assertThat(match.getTeam1Score()).isEqualTo(13);
            assertThat(match.getCourtNumber()).isEqualTo(first.getCourtNumber());
        });
        assertThat(teams.findByTournamentId(eventId)).hasSize(2);
    }

    @Test
    void teamPlayoffRestoresTheQueueCourtAndStatisticsWithoutRollingBackPaymentFlag() {
        long event = event(TournamentType.AMERICANO_TEAMS, Modalidad.DOBLES, 4);
        var first = addTeam(event);
        var second = addTeam(event);
        var match = playoff.queueQualificationMatch(event, first.getId(), second.getId());
        long queued = version(event, ConductChange.Action.QUEUE_MATCH);
        playoff.assignCourtToQueuedMatch(match.getId(), 1);
        playoff.submitQualResult(match.getId(), 6, 3);
        var paidTeam = teams.findById(first.getId()).orElseThrow();
        paidTeam.setHasPaid(true);
        teams.saveAndFlush(paidTeam);

        restore(event, queued);
        assertThat(playoff.getQueue(event)).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(match.getId());
            assertThat(item.getCourtNumber()).isNull();
            assertThat(item.getTeam1Games()).isNull();
        });
        var restoredTeam = teams.findById(first.getId()).orElseThrow();
        assertThat(restoredTeam.getHasPaid()).isTrue();
        assertThat(restoredTeam.getMatchesPlayed()).isZero();
        playoff.assignCourtToQueuedMatch(match.getId(), 1);
        playoff.submitQualResult(match.getId(), 6, 3);
        assertThat(playoff.getQueue(event)).isEmpty();
    }

    @Test
    void changedRegistrationBlocksRestoreAndOtherClubsCannotReadHistory() {
        long event = event(TournamentType.KING_OF_COURT, Modalidad.INDIVIDUAL, 4);
        registerFourPlayers(event);
        kingOfCourt.initializeTournament(owner, event, 1, 1);
        long version = version(event, ConductChange.Action.START);
        var registration = registrations.findByTournamentIdOrderByPositionAscWaitlistPositionAsc(event).get(0);
        registration.setStatus(RegistrationStatus.CANCELLED);
        registrations.saveAndFlush(registration);

        assertThat(history.preview(event, version, owner).registrationsChanged()).isTrue();
        assertThatThrownBy(() -> restore(event, version)).hasMessage("conduct.history.roster_changed");
        Owner otherClub = Owner.builder().id(90002L).role(OwnerRole.CLUB_ADMIN).clubId(-1L).build();
        assertThatThrownBy(() -> history.list(event, otherClub)).isInstanceOf(AccessDeniedException.class);
    }

    private long event(TournamentType type, Modalidad modality, int capacity) {
        Club club = clubs.save(Club.builder().nombre("Club " + UUID.randomUUID()).isActive(true).build());
        return tournaments.save(Tournament.builder().clubId(club.getId()).nombre("Conduct QA")
                .fechaInicio(LocalDate.now().plusDays(2)).horaInicio(LocalTime.NOON).generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7).tipo(type).modalidad(modality).cupoMax(capacity).precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO).contactoOrganizador("conduct@example.com").isActive(true)
                .build()).getId();
    }

    private void registerFourPlayers(long eventId) {
        Tournament event = tournaments.findById(eventId).orElseThrow();
        for (int i = 0; i < 4; i++) {
            registrations.save(TournamentRegistration.builder().tournament(event).player(player())
                    .status(RegistrationStatus.CONFIRMED).position(i + 1).build());
        }
        entityManager.flush();
        entityManager.clear();
    }

    private PlayerPadel player() {
        String suffix = UUID.randomUUID().toString();
        return players.save(PlayerPadel.builder().nombre("QA").apellido(suffix.substring(0, 6))
                .email(suffix + "@example.com").passwordHash("$2b$12$test").activo(true)
                .emailConfirmado(true).oauth2User(false).preferredLocale("es").nivelJugador(Nivel.C7).build());
    }

    private AmericanoTeam addTeam(long event) {
        var request = new TeamPlayoffAddTeamRequest();
        request.setPlayer1Id(player().getId());
        request.setPlayer2Id(player().getId());
        request.setAttended(true);
        return playoff.addTeam(event, request);
    }

    private void score(long king, int winnersScore, int losersScore) {
        KingOfCourtStateDTO state = kingOfCourt.getCurrentState(king);
        CourtDTO court = state.getCourts().get(0);
        List<Long> winners = court.getTeams().get(0).getPlayers().stream().map(player -> player.getId()).toList();
        List<Long> losers = court.getTeams().get(1).getPlayers().stream().map(player -> player.getId()).toList();
        kingOfCourt.saveMatchResult(state.getCurrentRoundId(), court.getCourtNumber(), winners, losers,
                winnersScore, losersScore);
    }

    private AmericanoMatchResultDto result(long match, int score) {
        return AmericanoMatchResultDto.builder().matchId(match).team1Score(score).team2Score(21 - score).build();
    }

    private long version(long event, ConductChange.Action action) {
        return history.list(event, owner).stream().filter(item -> item.action() == action).findFirst().orElseThrow().id();
    }

    private void restore(long event, long version) {
        history.restore(event, version, history.revision(event), owner);
    }
}
