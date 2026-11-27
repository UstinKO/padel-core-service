package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffAddTeamRequest;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.padelcoreservice.model.enums.PlayoffStage;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Безопасные назначения 1/8 до завершения квалификации и обычный посев после неё. */
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
class TeamPlayoffEarlyStageTest {

    @Autowired
    private TeamPlayoffService playoffService;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private AmericanoTeamRepository teamRepository;
    @Autowired
    private AmericanoMatchRepository matchRepository;

    @Autowired
    private PlayoffPairExchange exchange;
    @Autowired
    private TeamPlayoffCourts courts;

    @Test
    void exchangesQueuedEarlyMatchesAndKeepsTheNewTeamsOnCourt() {
        long tournament = createTournament(15);
        var teams = createTeams(tournament, 15);
        playoffService.initQualification(tournament, 5);

        for (int team = 1; team < 14; team += 2) {
            completeQualificationMatch(tournament, teams, team, team + 1);
        }
        completeQualificationMatch(tournament, teams, 15, 1);
        for (int team = 2; team <= 10; team += 2) {
            completeQualificationMatch(tournament, teams, team, team + 1);
        }
        playoffService.createQualificationMatch(tournament, teams.get(11).getId(), teams.get(12).getId(), 1);
        playoffService.createQualificationMatch(tournament, teams.get(13).getId(), teams.get(14).getId(), 2);

        var first = playoffService.confirmEarlyPlayoff(tournament, teams.get(1).getId(), teams.get(4).getId(),
                null, true);
        var second = playoffService.confirmEarlyPlayoff(tournament, teams.get(3).getId(), teams.get(6).getId(),
                null, true);
        var request = new PlayoffPairExchange.Request(1, second.getId(), 2, false, false);
        assertThat(exchange.options(first.getId(), 1, false).candidates())
                .anyMatch(option -> option.matchId().equals(second.getId()) && option.slot() == 2
                        && option.block() == null);
        exchange.exchange(first.getId(), request);

        assertThat(playoffService.getQueue(tournament)).extracting(match -> match.getId())
                .contains(first.getId(), second.getId());
        assertThat(matchRepository.findById(first.getId()).orElseThrow().getTeam1Id())
                .isEqualTo(teams.get(6).getId());
        assertThat(matchRepository.findById(second.getId()).orElseThrow().getTeam2Id())
                .isEqualTo(teams.get(1).getId());

        var assigned = playoffService.assignCourtToQueuedMatch(first.getId(), 3);
        assertThat(assigned.getCourtNumber()).isEqualTo(3);
        assertThat(assigned.getTeam1Id()).isEqualTo(teams.get(6).getId());
        assertThat(assigned.getTeam2Id()).isEqualTo(teams.get(4).getId());
        assertThatThrownBy(() -> exchange.exchange(first.getId(), request)).hasMessage("admin.playoff.swap.started");

        var before = courts.options(first.getId());
        assertThat(before.courts()).extracting(TeamPlayoffCourts.Court::number).containsExactly(1, 2, 3, 4, 5);
        assertThatThrownBy(() -> courts.change(first.getId(),
                new TeamPlayoffCourts.Change(before.version(), 5, null, false)))
                .hasMessage("admin.playoff.courts.confirm");
        courts.change(first.getId(), new TeamPlayoffCourts.Change(before.version(), 5, null, true));
        assertThat(first.getCourtNumber()).isEqualTo(5);
        assertThat(first.getTeam1Id()).isEqualTo(teams.get(6).getId());
        assertThat(first.getTeam2Id()).isEqualTo(teams.get(4).getId());
        assertThatThrownBy(() -> courts.change(first.getId(),
                new TeamPlayoffCourts.Change(before.version(), 4, null, true)))
                .hasMessage("admin.playoff.courts.stale");

        var occupied = courts.options(first.getId());
        var qualification = occupied.courts().get(0).occupants().get(0);
        courts.change(first.getId(), new TeamPlayoffCourts.Change(occupied.version(), 1,
                qualification.getId(), true));
        assertThat(first.getCourtNumber()).isEqualTo(1);
        assertThat(matchRepository.findById(qualification.getId()).orElseThrow().getCourtNumber()).isEqualTo(5);

        var current = courts.options(first.getId());
        assertThatThrownBy(() -> courts.change(first.getId(),
                new TeamPlayoffCourts.Change(current.version(), 2, null, true)))
                .hasMessage("admin.playoff.courts.occupied");
        assertThatThrownBy(() -> courts.change(first.getId(),
                new TeamPlayoffCourts.Change(current.version(), 6, null, true)))
                .hasMessage("admin.playoff.courts.invalid");
        assertThatThrownBy(() -> courts.change(first.getId(),
                new TeamPlayoffCourts.Change(current.version(), null, null, true)))
                .hasMessage("admin.playoff.courts.queue_unavailable");

        var queuedVersion = courts.options(second.getId()).version();
        courts.change(second.getId(), new TeamPlayoffCourts.Change(queuedVersion, 4, null, false));
        assertThat(second.isInProgress()).isTrue();
        assertThat(courts.options(second.getId()).canQueue()).isTrue();
        courts.change(second.getId(), new TeamPlayoffCourts.Change(courts.options(second.getId()).version(),
                null, null, true));
        assertThat(second.isQueued()).isTrue();
        assertThat(second.getCourtNumber()).isNull();

        playoffService.submitPlayoffResult(first.getId(), 6, 4);
        courts.change(first.getId(), new TeamPlayoffCourts.Change(courts.options(first.getId()).version(),
                2, null, true));
        assertThat(first.isCompleted()).isTrue();
        assertThat(first.getTeam1Games()).isEqualTo(6);
        assertThat(first.getTeam2Games()).isEqualTo(4);
        assertThat(courts.options(first.getId()).courts().get(1).occupants())
                .noneMatch(match -> match.getId().equals(first.getId()));
        assertThat(playoffService.getQualRanking(tournament).isFinished()).isFalse();
    }

    private void completeQualificationMatch(long tournament, List<AmericanoTeam> teams, int first, int second) {
        var match = playoffService.createQualificationMatch(tournament, teams.get(first - 1).getId(),
                teams.get(second - 1).getId(), 1);
        playoffService.submitQualResult(match.getId(), 6, 4);
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 14, 15, 16})
    void confirmedMatchRemainsInSameBracketWhenLastQualificationScoreArrives(int count) {
        Long tournament = createTournament(count);
        var teams = createTeams(tournament, count);
        playoffService.initQualification(tournament, 5);

        Random scores = new Random(count <= 14 ? 8 : 2);
        for (int i = 0; i < count - 1; i++) {
            var fixture = playoffService.createQualificationMatch(tournament, teams.get(i).getId(),
                    teams.get(i + 1).getId(), 1);
            int losingGames = scores.nextInt(6);
            boolean firstWins = scores.nextBoolean();
            playoffService.submitQualResult(fixture.getId(), firstWins ? 6 : losingGames,
                    firstWins ? losingGames : 6);
        }
        var last = playoffService.createQualificationMatch(tournament, teams.get(count - 1).getId(),
                teams.get(0).getId(), 1);

        assertThatThrownBy(() -> playoffService.submitQualResult(last.getId(), 5, 4))
                .hasMessage("team_playoff.early.invalid_score");
        assertThat(playoffService.canInitPlayoff(tournament)).isFalse();
        var options = playoffService.earlyPlayoff(tournament, null, null);
        assertThat(options.finishedTeams()).isEqualTo(count - 2);
        assertThat(options.freeCourts()).containsExactly(2, 3, 4, 5);
        assertThat(options.matches().size()).isGreaterThanOrEqualTo(2);
        var suggestion = options.matches().get(0);
        long first = suggestion.first().id();
        long second = suggestion.second().id();

        assertThat(playoffService.confirmEarlyPlayoff(tournament, first, second, 2).getCourtNumber()).isEqualTo(2);
        var confirmed = playoffService.confirmEarlyPlayoff(tournament, second, first, 2);
        assertThat(playoffService.getPlayoffMatches(tournament)).hasSize(1);
        var secondSuggestion = options.matches().get(1);
        boolean manualOverride = count == 15;
        if (manualOverride) {
            var current = playoffService.earlyPlayoff(tournament, null, null);
            secondSuggestion = current.readyTeams().stream().flatMap(left -> current.readyTeams().stream()
                            .filter(right -> left.id() < right.id())
                            .filter(right -> "override".equals(playoffService
                                    .earlyPlayoff(tournament, left.id(), right.id()).manualStatus()))
                            .map(right -> new TeamPlayoffService.EarlyMatch(left, right)))
                    .findFirst().orElseThrow();
        }
        var queued = playoffService.confirmEarlyPlayoff(tournament, secondSuggestion.first().id(),
                secondSuggestion.second().id(), null, manualOverride);
        assertThat(queued.isQueued()).isTrue();
        assertThat(playoffService.getQueue(tournament)).anyMatch(m -> m.getId().equals(queued.getId())
                && "ROUND_OF_16".equals(m.getPlayoffStage()));
        assertThatThrownBy(() -> playoffService.assignCourtToQueuedMatch(queued.getId(), 1))
                .hasMessageContaining("ocupada");
        playoffService.submitPlayoffResult(confirmed.getId(), 6, 5);
        assertThat(playoffService.getQualRanking(tournament).isFinished()).isFalse();

        playoffService.submitQualResult(last.getId(), 5, 6);
        assertThat(playoffService.getQualRanking(tournament).isFinished()).isTrue();
        var roundOf16 = matchRepository.findByTournamentIdAndPlayoffStage(tournament, PlayoffStage.ROUND_OF_16);
        assertThat(roundOf16).hasSize(count - 8);
        assertThat(roundOf16).anyMatch(match -> match.getId().equals(queued.getId()));
        assertThat(roundOf16).filteredOn(m -> m.getId().equals(confirmed.getId())).singleElement().satisfies(m -> {
            assertThat(m.getTeam1Id()).isEqualTo(first);
            assertThat(m.getTeam2Id()).isEqualTo(second);
            assertThat(m.getTeam1Games()).isEqualTo(6);
            assertThat(m.getTeam2Games()).isEqualTo(5);
            if (count < 16) {
                var next = matchRepository.findById(m.getNextMatchId()).orElseThrow();
                assertThat(m.getNextMatchSlot() == 1 ? next.getTeam1Id() : next.getTeam2Id()).isEqualTo(first);
            }
        });
        assertThat(roundOf16.stream().filter(AmericanoMatch::isQueued)).hasSize(count - 9);
        if (count == 16) verifyEarlySemifinal(tournament, roundOf16);
    }

    private void verifyEarlySemifinal(Long tournament, List<AmericanoMatch> roundOf16) {
        for (var match : roundOf16) {
            if (match.isCompleted()) continue;
            playoffService.assignCourtToQueuedMatch(match.getId(), 1);
            playoffService.submitPlayoffResult(match.getId(), 6, 2);
        }

        var quarters = matchRepository.findByTournamentIdAndPlayoffStage(tournament, PlayoffStage.QUARTER_FINAL);
        assertThat(quarters).hasSize(4);
        var history = matchRepository.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournament);
        var first = quarters.get(0);
        var second = quarters.get(1);
        var combination = java.util.stream.Stream.of(first.getTeam1Id(), first.getTeam2Id())
                .flatMap(a -> java.util.stream.Stream.of(second.getTeam1Id(), second.getTeam2Id())
                        .filter(b -> history.stream().filter(AmericanoMatch::isCompleted)
                                .noneMatch(previous -> (a.equals(previous.getTeam1Id()) && b.equals(previous.getTeam2Id()))
                                        || (b.equals(previous.getTeam1Id()) && a.equals(previous.getTeam2Id()))))
                        .map(b -> List.of(a, b))).findFirst().orElseThrow();
        finishQuarter(first, combination.get(0));
        finishQuarter(second, combination.get(1));
        var options = playoffService.earlyPlayoff(tournament, combination.get(0), combination.get(1));
        assertThat(options.semifinal()).isTrue();
        assertThat(options.manualStatus()).isEqualTo("safe");
        var early = playoffService.confirmEarlyPlayoff(tournament, combination.get(0), combination.get(1), 2);
        assertThat(early.getPlayoffStage()).isEqualTo(PlayoffStage.SEMI_FINAL);

        finishQuarter(quarters.get(2), quarters.get(2).getTeam1Id());
        finishQuarter(quarters.get(3), quarters.get(3).getTeam1Id());
        var semifinals = matchRepository.findByTournamentIdAndPlayoffStage(tournament, PlayoffStage.SEMI_FINAL);
        assertThat(semifinals).hasSize(2).allMatch(match -> match.getTeam1Id() != null && match.getTeam2Id() != null);
        assertThat(semifinals).filteredOn(match -> match.getId().equals(early.getId())).singleElement().satisfies(match -> {
            assertThat(match.getTeam1Id()).isEqualTo(combination.get(0));
            assertThat(match.getTeam2Id()).isEqualTo(combination.get(1));
            assertThat(match.getCourtNumber()).isEqualTo(2);
        });
    }

    private void finishQuarter(AmericanoMatch match, Long winner) {
        playoffService.assignCourtToQueuedMatch(match.getId(), 1);
        boolean firstWins = winner.equals(match.getTeam1Id());
        playoffService.submitPlayoffResult(match.getId(), firstWins ? 6 : 2, firstWins ? 2 : 6);
    }

    @Test
    void eightTeamsWaitForQualificationBeforeStartingQuarterfinals() {
        Long tournamentId = createTournament(8);
        List<AmericanoTeam> teams = createTeams(tournamentId, 8);
        playoffService.initQualification(tournamentId, 8);

        for (int i = 0; i < 6; i += 2) {
            completeFullQualificationFor(tournamentId, teams.get(i), teams.get(i + 1));
        }
        assertThat(playoffService.canInitPlayoff(tournamentId)).isFalse();
        assertThat(playoffService.earlyPlayoff(tournamentId, null, null).available()).isFalse();

        completeFullQualificationFor(tournamentId, teams.get(6), teams.get(7));
        assertThat(playoffService.canInitPlayoff(tournamentId)).isTrue();
        playoffService.initPlayoff(tournamentId);
        assertThat(matchRepository.findByTournamentIdAndPlayoffStage(tournamentId, PlayoffStage.QUARTER_FINAL))
                .hasSize(4);
    }

    @Test
    void canInitPlayoff_falseForNineTeams_untilQualificationFullyDone() {
        Long tournamentId = createTournament(9);
        List<AmericanoTeam> teams = createTeams(tournamentId, 9);
        playoffService.initQualification(tournamentId, 8);

        completeFullQualificationFor(tournamentId, teams.get(0), teams.get(1));
        completeFullQualificationFor(tournamentId, teams.get(2), teams.get(3));

        assertThat(playoffService.canInitPlayoff(tournamentId))
                .as("9 teams need play-in — early creation stays disabled regardless of how many teams settled")
                .isFalse();
    }

    private Long createTournament(int teamCount) {
        String suffix = "LFPT465-" + teamCount + "-" + UUID.randomUUID();

        Club club = clubRepository.save(Club.builder()
                .nombre("Club " + suffix)
                .isActive(true)
                .build());

        Tournament tournament = tournamentRepository.save(Tournament.builder()
                .clubId(club.getId())
                .nombre("Torneo " + suffix)
                .fechaInicio(LocalDate.now().plusDays(7))
                .horaInicio(LocalTime.of(10, 0))
                .generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7)
                .tipo(TournamentType.AMERICANO_TEAMS)
                .modalidad(Modalidad.DOBLES)
                .cupoMax(teamCount * 2)
                .precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("test@example.com")
                .isActive(true)
                .build());

        return tournament.getId();
    }

    private List<AmericanoTeam> createTeams(Long tournamentId, int teamCount) {
        List<AmericanoTeam> teams = new ArrayList<>();
        for (int i = 0; i < teamCount; i++) {
            PlayerPadel p1 = createPlayer(tournamentId, i, 1);
            PlayerPadel p2 = createPlayer(tournamentId, i, 2);

            TeamPlayoffAddTeamRequest req = new TeamPlayoffAddTeamRequest();
            req.setPlayer1Id(p1.getId());
            req.setPlayer2Id(p2.getId());
            req.setRegistrationSource("ADMIN_MANUAL");
            req.setHasPaid(true);
            req.setAttended(true);

            teams.add(playoffService.addTeam(tournamentId, req));
        }
        return teams;
    }

    private PlayerPadel createPlayer(Long tournamentId, int teamIndex, int slot) {
        String suffix = tournamentId + "-" + teamIndex + "-" + slot;
        return playerRepository.save(PlayerPadel.builder()
                .nombre("LFPT465")
                .apellido("Player" + suffix)
                .email("lfpt465-" + suffix + "@example.com")
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }

    /** Отыгрывает оба квалификационных матча для данной пары команд (друг против друга) — всегда победа первой. */
    private void completeFullQualificationFor(Long tournamentId, AmericanoTeam a, AmericanoTeam b) {
        AmericanoMatch m1 = playoffService.createQualificationMatch(tournamentId, a.getId(), b.getId(), 1);
        playoffService.submitQualResult(m1.getId(), 6, 2);
        AmericanoMatch m2 = playoffService.createQualificationMatch(tournamentId, a.getId(), b.getId(), 1);
        playoffService.submitQualResult(m2.getId(), 6, 3);
    }

}
