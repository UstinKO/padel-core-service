package com.padle.core.padelcoreservice.service.americano;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchSuggestionDto;
import com.padle.core.padelcoreservice.dto.live.PublicLiveDtos;
import com.padle.core.padelcoreservice.service.PublicLiveService;
import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffAddTeamRequest;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.tournament.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Modalidad;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.padelcoreservice.model.enums.PlayoffStage;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.application.tournament.TournamentStatus;
import com.padle.core.application.tournament.TournamentType;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.persistence.tournament.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import com.padle.core.padelcoreservice.service.conduct.ConductChange;
import com.padle.core.padelcoreservice.service.conduct.TournamentConductHistory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Сквозные regression-тесты полного цикла Team Playoff (квалификация → плей-офф → финал)
 * для каждого N от 8 до 16 — весь поддерживаемый диапазон.
 * <p>
 * Гоняет турнир через реальный {@link TeamPlayoffService} (не HTTP) с реальной БД —
 * {@code @Transactional} на тесте откатывает все изменения после каждого прогона,
 * поэтому общие dev-данные (турнир из ручной проверки и т.п.) не затрагиваются.
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
class TeamPlayoffEndToEndTest {

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
    private TournamentConductHistory history;
    @Autowired
    private PlayoffPairExchange exchange;
    @Autowired
    private org.springframework.jdbc.core.simple.JdbcClient jdbc;
    @Autowired
    private PublicLiveService publicLive;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogCapture() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(TeamPlayoffService.class)).addAppender(logAppender);
    }

    @AfterEach
    void detachLogCapture() {
        ((Logger) LoggerFactory.getLogger(TeamPlayoffService.class)).detachAppender(logAppender);
    }

    @ParameterizedTest
    @CsvSource({"8,4", "8,5", "9,4", "9,5", "10,4", "10,5", "11,4", "11,5", "12,4", "12,5",
            "13,4", "13,5", "14,4", "14,5", "15,4", "15,5", "16,4", "16,5"})
    void asynchronousQualificationUsesAllCourtsAndRechecksEveryAssignment(int count, int courts) {
        long tournament = createTournament(count);
        var teams = createTeams(tournament, count);
        playoffService.initQualification(tournament, courts);
        var running = new HashMap<Long, Integer>();
        for (int tick = 0; tick < 300; tick++) {
            final int now = tick;
            var completed = running.entrySet().stream().filter(e -> e.getValue() <= now).map(Map.Entry::getKey).toList();
            for (long id : completed) {
                playoffService.submitQualResult(id, id % 2 == 0 ? 6 : 3, id % 2 == 0 ? 3 : 6);
                running.remove(id);
            }
            var occupied = playoffService.getActiveMatches(tournament).stream()
                    .map(AmericanoMatch::getCourtNumber).collect(Collectors.toSet());
            var suggestions = playoffService.suggestSecondRoundPairings(tournament).iterator();
            for (int court = 1; court <= courts && suggestions.hasNext(); court++) {
                if (occupied.contains(court)) continue;
                var pair = suggestions.next();
                var match = playoffService.createQualificationMatch(tournament, pair.getTeam1().getId(), pair.getTeam2().getId(), court);
                running.put(match.getId(), tick + 5 + (int) (match.getId() % 17));
            }
            if (playoffService.isQualificationDone(tournament)) break;
        }
        assertThat(playoffService.isQualificationDone(tournament)).isTrue();
        assertEachTeamPlayedExactlyTwoQualMatches(tournament, teams);
        assertNoRematchesWithinQualification(tournament);
    }

    @Test
    void positiveTwelveAndFirstSavedResultTimeSurviveCorrectionAndQueue() {
        long tournament = createTournament(12);
        var teams = createTeams(tournament, 12);
        playoffService.initQualification(tournament, 5);
        AmericanoMatch first = null;
        for (int team = 0; team < 12; team += 2) {
            var match = playoffService.createQualificationMatch(tournament, teams.get(team).getId(), teams.get(team + 1).getId(), 1);
            playoffService.submitQualResult(match.getId(), 6, 2);
            if (first == null) first = match;
        }
        var completed = first.getCompletedAt();
        assertThat(completed).isNotNull();
        playoffService.submitQualResult(first.getId(), 6, 4);
        playoffService.submitQualResult(first.getId(), 6, 4);
        assertThat(first.getCompletedAt()).isEqualTo(completed);
        matchRepository.flush();
        var stored = jdbc.sql("SELECT completed_at FROM americano_matches_db WHERE id = :id")
                .param("id", first.getId()).query(java.time.LocalDateTime.class).single();
        var legacySnapshot = jdbc.sql("SELECT CAST(to_jsonb(m) - 'pairing_confirmed' - 'pairing_override_reason' - 'completed_at' AS text) FROM americano_matches_db m WHERE id = :id")
                .param("id", first.getId()).query(String.class).single();
        jdbc.sql("DELETE FROM americano_matches_db WHERE id = :id").param("id", first.getId()).update();
        jdbc.sql("INSERT INTO americano_matches_db SELECT * FROM jsonb_populate_record(NULL::americano_matches_db, CAST(:row AS jsonb))")
                .param("row", legacySnapshot).update();
        assertThat(jdbc.sql("SELECT completed_at FROM americano_matches_db WHERE id = :id")
                .param("id", first.getId()).query(java.time.LocalDateTime.class).single()).isEqualTo(stored);
        assertThat(jdbc.sql("SELECT pairing_confirmed FROM americano_matches_db WHERE id = :id")
                .param("id", first.getId()).query(Boolean.class).single()).isTrue();
        for (var pair : List.of(new int[]{2,4}, new int[]{6,8}, new int[]{1,3}, new int[]{5,7}, new int[]{9,11}, new int[]{10,12})) {
            var match = playoffService.queueQualificationMatch(tournament, teams.get(pair[0]-1).getId(), teams.get(pair[1]-1).getId());
            assertThat(playoffService.getQueue(tournament)).anySatisfy(queued -> {
                assertThat(queued.getId()).isEqualTo(match.getId());
                assertThat(queued.getTeam1WaitingSeconds()).isNotNull();
                assertThat(queued.getTeam2WaitingSeconds()).isNotNull();
            });
            playoffService.assignCourtToQueuedMatch(match.getId(), 1);
            playoffService.submitQualResult(match.getId(), 6, 2);
        }
        assertNoRematchesWithinQualification(tournament);
        assertEachTeamPlayedExactlyTwoQualMatches(tournament, teams);
    }

    @ParameterizedTest(name = "{0} equipos")
    @ValueSource(ints = {8, 9, 10, 11, 12, 13, 14, 15, 16})
    void fullTournament_satisfiesAllInvariants(int teamCount) {
        Long tournamentId = createTournament(teamCount);
        List<AmericanoTeam> teams = createTeams(tournamentId, teamCount);

        runQualification(tournamentId, teams);
        assertEachTeamPlayedExactlyTwoQualMatches(tournamentId, teams);
        assertNoRematchesWithinQualification(tournamentId);

        playoffService.initPlayoff(tournamentId);
        assertQuarterFinalSeedingMatchesSpecTable(tournamentId, teamCount);
        assertPublicSnapshotMatchesBracket(tournamentId);

        playThroughPlayoff(tournamentId);
        assertNoUnexplainedRematchesInPlayoff(tournamentId);
        assertChampionAndRunnerUpDetermined(tournamentId);
        assertPublicSnapshotMatchesBracket(tournamentId);
        assertThat(publicLive.snapshot(tournamentId).metadata().phase().name()).isEqualTo("FINISHED");
        assertEliminatedTeamsExposeCorrectStage(tournamentId, teams);
    }

    /**
     * Из 8 зарегистрированных команд 1 отмечается «не пришла» до начала игр —
     * квалификация проходит только между оставшимися 7, и {@code canInitPlayoff}/{@code initPlayoff}
     * считают 7 команд: команда NO_SHOW не попадает в посев плей-офф, хотя при 8 командах
     * (степень двойки) попала бы.
     */
    @Test
    void noShowTeam_excludedFromQualificationCountAndPlayoffSeeding() {
        Long tournamentId = createTournament(8);
        List<AmericanoTeam> teams = createTeams(tournamentId, 8);

        AmericanoTeam noShowTeam = teams.get(7);
        playoffService.markNoShow(noShowTeam.getId());

        List<AmericanoTeam> activeTeams = teams.subList(0, 7);
        runQualification(tournamentId, activeTeams);
        assertEachTeamPlayedExactlyTwoQualMatches(tournamentId, activeTeams);

        assertThat(playoffService.canInitPlayoff(tournamentId)).isTrue();

        playoffService.initPlayoff(tournamentId);

        List<AmericanoMatch> playoffMatches = matchRepository
                .findByTournamentIdAndPhase(tournamentId, TournamentPhase.PLAYOFF);
        assertThat(playoffMatches).isNotEmpty();
        boolean noShowTeamSeeded = playoffMatches.stream()
                .anyMatch(m -> noShowTeam.getId().equals(m.getTeam1Id()) || noShowTeam.getId().equals(m.getTeam2Id()));
        assertThat(noShowTeamSeeded)
                .as("команда NO_SHOW не должна попасть ни в один матч плей-офф")
                .isFalse();

        // 7 активных команд (не степень двойки) → сетка на 4 (largestPowerOf2(7)), как если бы
        // в турнире изначально было 7 команд.
        long seededTeams = playoffMatches.stream()
                .flatMap(m -> Stream.of(m.getTeam1Id(), m.getTeam2Id()))
                .filter(Objects::nonNull)
                .distinct()
                .count();
        assertThat(seededTeams).isEqualTo(4);
    }

    /**
     * Команды приходят не одновременно — квалификация стартует всего от 2 команд,
     * не дожидаясь полного набора на четвертьфинал. Плей-офф здесь намеренно не проверяется —
     * он требует достаточного числа команд и стартует отдельной кнопкой позже.
     */
    @ParameterizedTest(name = "{0} equipos (solo calificación)")
    @ValueSource(ints = {2, 3})
    void qualificationStartsWithFewerThanFourTeams(int teamCount) {
        Long tournamentId = createTournament(teamCount);
        List<AmericanoTeam> teams = createTeams(tournamentId, teamCount);

        runQualification(tournamentId, teams);

        assertEachTeamPlayedExactlyTwoQualMatches(tournamentId, teams);
    }

    @Test
    void restoresBracketWithItsOriginalMatchLinksAfterRegenerationAndResult() {
        long tournament = createTournament(10);
        runQualification(tournament, createTeams(tournament, 10));
        playoffService.initPlayoff(tournament);
        var original = playoffService.getPlayoffMatches(tournament).stream().map(BracketMatch::of).toList();
        playoffService.regeneratePlayoff(tournament);
        var playIn = playoffService.getPlayoffMatches(tournament).stream()
                .filter(match -> match.getNextMatchId() != null).findFirst().orElseThrow();
        playoffService.submitPlayoffResult(playIn.getId(), 6, 3);

        Owner owner = Owner.builder().id(90001L).role(OwnerRole.SUPER_ADMIN).build();
        long beforeRegenerate = history.list(tournament, owner).stream()
                .filter(version -> version.action() == ConductChange.Action.CHECKPOINT_REGENERATE_PLAYOFF)
                .findFirst().orElseThrow().id();
        history.restore(tournament, beforeRegenerate, history.revision(tournament), owner);

        assertThat(playoffService.getPlayoffMatches(tournament)).map(BracketMatch::of)
                .containsExactlyInAnyOrderElementsOf(original);
        assertThat(history.list(tournament, owner)).extracting(TournamentConductHistory.Version::action)
                .contains(ConductChange.Action.REGENERATE_PLAYOFF, ConductChange.Action.CHECKPOINT_RESTORE);
    }

    @Test
    void exchangeMovesPlayInLinkPreservesHistoryAndAdvancesWinners() {
        long tournament = createTournament(9);
        runQualification(tournament, createTeams(tournament, 9));
        playoffService.initPlayoff(tournament);
        var playIn = playoffService.getPlayoffMatches(tournament).stream()
                .filter(m -> m.getPlayoffStage() == PlayoffStage.ROUND_OF_16).findFirst().orElseThrow();
        playoffService.submitPlayoffResult(playIn.getId(), 6, 3);
        Long sourceId = playIn.getNextMatchId();
        int sourceSlot = playIn.getNextMatchSlot();
        var source = matchRepository.findById(sourceId).orElseThrow();
        source.setPlayoffPlayStarted(null);
        var preview = exchange.options(sourceId, sourceSlot, false);
        var selected = preview.candidates().stream()
                .filter(option -> option.block() == null && !option.encounters().isEmpty())
                .findFirst().orElseThrow();
        assertThat(selected.confirmNotStarted()).isTrue();
        var command = new PlayoffPairExchange.Request(sourceSlot, selected.matchId(), selected.slot(), true, true);
        var oldQualification = matchRepository.findByTournamentIdAndPhase(tournament, TournamentPhase.QUALIFICATION)
                .stream().map(BracketMatch::of).toList();
        var saved = exchange.exchange(sourceId, command);
        assertThat(saved.rematchConfirmed()).isTrue();
        assertThat(saved.firstBefore()).isEqualTo(preview.current());
        assertThat(saved.firstAfter()).isEqualTo(selected.firstAfter());
        assertThat(playIn.getNextMatchId()).isEqualTo(selected.matchId());
        assertThat(playIn.getNextMatchSlot()).isEqualTo(selected.slot());
        assertThat(playIn.getTeam1Games()).isEqualTo(6);
        assertThat(playIn.getTeam2Games()).isEqualTo(3);
        assertThat(matchRepository.findByTournamentIdAndPhase(tournament, TournamentPhase.QUALIFICATION))
                .map(BracketMatch::of).containsExactlyElementsOf(oldQualification);
        var owner = Owner.builder().id(90001L).role(OwnerRole.SUPER_ADMIN).build();
        var latest = history.list(tournament, owner).get(0);
        assertThat(latest.action()).isEqualTo(ConductChange.Action.PLAYOFF_SWAP);
        assertThat(history.preview(tournament, latest.id(), owner).playoffSwap()).isEqualTo(saved);
        assertThat(history.list(tournament, owner)).extracting(TournamentConductHistory.Version::action)
                .contains(ConductChange.Action.CHECKPOINT_PLAYOFF_SWAP);

        var qf = matchRepository.findByTournamentIdAndPlayoffStage(tournament, PlayoffStage.QUARTER_FINAL);
        for (var match : qf) {
            playoffService.submitPlayoffResultForPair(match.getId(), 6, 4, match.getTeam1Id(), match.getTeam2Id());
        }
        assertThat(matchRepository.findByTournamentIdAndPlayoffStage(tournament, PlayoffStage.SEMI_FINAL))
                .flatExtracting(match -> List.of(match.getTeam1Id(), match.getTeam2Id()))
                .containsExactlyInAnyOrderElementsOf(qf.stream().map(AmericanoMatch::getTeam1Id).toList());
    }

    @Test
    void completedMatchCanOnlyBeCorrectedWithNewScoreAndOldScoreIsAudited() {
        long tournament = createTournament(8);
        runQualification(tournament, createTeams(tournament, 8));
        playoffService.initPlayoff(tournament);
        var quarterfinals = matchRepository.findByTournamentIdAndPlayoffStage(tournament, PlayoffStage.QUARTER_FINAL);
        var completed = quarterfinals.get(0);
        var started = quarterfinals.get(1);
        exchange.markStarted(started.getId());
        playoffService.submitPlayoffResult(completed.getId(), 5, 6);
        assertThat(exchange.options(completed.getId(), 1, false).block()).endsWith(".completed");
        assertThat(exchange.options(started.getId(), 1, false).block()).endsWith(".started");
        var selected = exchange.options(completed.getId(), 1, true).candidates().stream()
                .filter(option -> option.matchId().equals(started.getId()) && option.block() == null)
                .findFirst().orElseThrow();
        var command = new PlayoffPairExchange.Request(1, selected.matchId(), selected.slot(), true, true);
        assertThatThrownBy(() -> exchange.correct(completed.getId(),
                new PlayoffPairExchange.Correction(command, true, null, null)))
                .isInstanceOf(InvalidStateException.class).hasMessageEndingWith(".correct_score");
        var saved = exchange.correct(completed.getId(), new PlayoffPairExchange.Correction(command, true,
                new PlayoffPairExchange.Score(6, 2), new PlayoffPairExchange.Score(4, 6)));
        assertThat(saved.firstBefore().firstGames()).isEqualTo(5);
        assertThat(saved.firstBefore().secondGames()).isEqualTo(6);
        assertThat(saved.firstAfter().firstGames()).isEqualTo(6);
        assertThat(saved.firstAfter().secondGames()).isEqualTo(2);
        assertThat(started.isCompleted()).isTrue();
        var owner = Owner.builder().id(90001L).role(OwnerRole.SUPER_ADMIN).build();
        var latest = history.list(tournament, owner).get(0);
        assertThat(latest.action()).isEqualTo(ConductChange.Action.PLAYOFF_PAIR_CORRECTED);
        assertThat(history.preview(tournament, latest.id(), owner).playoffSwap()).isEqualTo(saved);
        assertThatThrownBy(() -> playoffService.submitPlayoffResultForPair(completed.getId(), 3, 6,
                saved.firstBefore().first().id(), saved.firstBefore().second().id()))
                .isInstanceOf(InvalidStateException.class).hasMessage("conduct.history.stale");
    }

    private record BracketMatch(Long id, Long first, Long second, Long next, Integer slot) {
        static BracketMatch of(AmericanoMatch match) {
            return new BracketMatch(match.getId(), match.getTeam1Id(), match.getTeam2Id(),
                    match.getNextMatchId(), match.getNextMatchSlot());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SETUP
    // ═══════════════════════════════════════════════════════════════════════

    private Long createTournament(int teamCount) {
        String suffix = "T17-" + teamCount + "-" + UUID.randomUUID();

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
                .categoriaNivel(com.padle.core.application.tournament.Nivel.C7)
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

    private void assertPublicSnapshotMatchesBracket(long tournamentId) {
        var snapshot=(PublicLiveDtos.Playoff) publicLive.snapshot(tournamentId);
        var exposed=snapshot.rounds().stream().filter(round -> "PLAYOFF".equals(round.phase())).flatMap(round -> round.matches().stream()).toList();
        var entities=matchRepository.findByTournamentIdAndPhase(tournamentId,TournamentPhase.PLAYOFF);
        assertThat(exposed).hasSize(entities.size());
        for(var entity:entities) {
            var match=exposed.stream().filter(row -> row.id().equals(entity.getId())).findFirst().orElseThrow();
            assertThat(match.nextMatchId()).isEqualTo(entity.getNextMatchId());
            assertThat(match.nextSlot()).isEqualTo(entity.getNextMatchSlot());
            assertThat(match.first().teamId()).isEqualTo(entity.getTeam1Id());
            assertThat(match.second().teamId()).isEqualTo(entity.getTeam2Id());
            assertThat(match.firstWinner()).isEqualTo(entity.outcome().firstWinner());
            if (entity.isCompleted()) assertThat(match.firstWinner()).isNotNull();
            assertThat(match.first().games()).isEqualTo(entity.getTeam1Games());
            assertThat(match.second().games()).isEqualTo(entity.getTeam2Games());
        }
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
                .nombre("T17")
                .apellido("Player" + suffix)
                .email("t17-" + suffix + "@example.com")
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // КВАЛИФИКАЦИЯ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Прогоняет квалификацию через реальный court-driven механизм: сначала попарно
     * назначает всех "нетронутых" команд (оставляя, при нечётном N, одну "дополнительную"),
     * затем следует предложениям {@link TeamPlayoffService#suggestSecondRoundPairings}
     * до тех пор, пока каждая команда не сыграет ровно 2 матча.
     */
    private void runQualification(Long tournamentId, List<AmericanoTeam> teams) {
        playoffService.initQualification(tournamentId, 8);

        List<AmericanoTeamDto> available = new ArrayList<>(
                playoffService.getAvailableTeamsForQualification(tournamentId));
        int matchIndex = 0;
        while (available.size() >= 2) {
            AmericanoTeamDto a = available.remove(0);
            AmericanoTeamDto b = available.remove(0);
            createAndCompleteQualMatch(tournamentId, a.getId(), b.getId(), matchIndex++);
        }

        int guard = 0;
        while (!allTeamsHaveTwoQualMatches(tournamentId, teams) && guard++ < teams.size() * 2) {
            List<AmericanoMatchSuggestionDto> suggestions = playoffService.suggestSecondRoundPairings(tournamentId);
            if (suggestions.isEmpty()) {
                break;
            }
            for (AmericanoMatchSuggestionDto s : suggestions) {
                createAndCompleteQualMatch(tournamentId, s.getTeam1().getId(), s.getTeam2().getId(), matchIndex++);
            }
        }
    }

    private void createAndCompleteQualMatch(Long tournamentId, Long team1Id, Long team2Id, int matchIndex) {
        AmericanoMatch match = playoffService.createQualificationMatch(tournamentId, team1Id, team2Id, 1);
        int loserGames = matchIndex % 6;
        playoffService.submitQualResult(match.getId(), 6, loserGames);
    }

    private boolean allTeamsHaveTwoQualMatches(Long tournamentId, List<AmericanoTeam> teams) {
        Map<Long, Long> completedByTeam = countCompletedMatchesByTeam(
                matchRepository.findByTournamentIdAndPhase(tournamentId, TournamentPhase.QUALIFICATION));
        return teams.stream().allMatch(t -> completedByTeam.getOrDefault(t.getId(), 0L) == 2);
    }

    private void assertEachTeamPlayedExactlyTwoQualMatches(Long tournamentId, List<AmericanoTeam> teams) {
        Map<Long, Long> completedByTeam = countCompletedMatchesByTeam(
                matchRepository.findByTournamentIdAndPhase(tournamentId, TournamentPhase.QUALIFICATION));
        for (AmericanoTeam team : teams) {
            assertThat(completedByTeam.getOrDefault(team.getId(), 0L))
                    .as("team %d played exactly 2 qualification matches", team.getId())
                    .isEqualTo(2L);
        }
    }

    /** Структурная гарантия: победитель-к-победителю/проигравший-к-проигравшему не может повторить пару 1-го тура. */
    private void assertNoRematchesWithinQualification(Long tournamentId) {
        List<AmericanoMatch> qualMatches = matchRepository.findByTournamentIdAndPhase(tournamentId, TournamentPhase.QUALIFICATION);
        Set<String> seenPairs = new HashSet<>();
        for (AmericanoMatch m : qualMatches) {
            String key = pairKey(m.getTeam1Id(), m.getTeam2Id());
            assertThat(seenPairs).as("no repeated pairing within qualification").doesNotContain(key);
            seenPairs.add(key);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ПЛЕЙ-ОФФ
    // ═══════════════════════════════════════════════════════════════════════

    /** Число напрямую посеянных команд и play-in-матчей точно соответствует таблице PlayoffFormat. */
    private void assertQuarterFinalSeedingMatchesSpecTable(Long tournamentId, int teamCount) {
        List<AmericanoMatch> qfMatches = matchRepository.findByTournamentIdAndPlayoffStage(tournamentId, PlayoffStage.QUARTER_FINAL);
        assertThat(qfMatches).hasSize(4);


        List<AmericanoMatch> playInMatches = matchRepository.findByTournamentIdAndPlayoffStage(tournamentId, PlayoffStage.ROUND_OF_16);
        assertThat(playInMatches).hasSize(PlayoffFormat.playInTeams(teamCount) / 2);
        var playInIds = playInMatches.stream().flatMap(m -> Stream.of(m.getTeam1Id(), m.getTeam2Id())).collect(Collectors.toSet());
        var expectedDirect = playoffService.getQualRanking(tournamentId).getRanking().stream()
                .limit(PlayoffFormat.directToQuarterFinal(teamCount)).map(AmericanoTeamDto::getId).collect(Collectors.toSet());
        assertThat(playInIds).doesNotContainAnyElementsOf(expectedDirect);
        assertThat(playInIds.size() + expectedDirect.size()).isEqualTo(teamCount);
    }

    /** Раз за разом доигрывает все текущие IN_PROGRESS матчи плей-офф (play-in → QF → SF → Final), пока турнир не завершится. */
    private void playThroughPlayoff(Long tournamentId) {
        int guard = 0;
        while (guard++ < 20) {
            for (var queued : playoffService.getQueue(tournamentId)) {
                var occupied = playoffService.getActiveMatches(tournamentId).stream().map(AmericanoMatch::getCourtNumber).collect(Collectors.toSet());
                var free = java.util.stream.IntStream.rangeClosed(1, playoffService.getConfiguredCourts(tournamentId))
                        .filter(c -> !occupied.contains(c)).findFirst();
                if (free.isPresent()) playoffService.assignCourtToQueuedMatch(queued.getId(), free.getAsInt());
            }
            List<Long> inProgressIds = matchRepository.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId)
                    .stream()
                    .filter(AmericanoMatch::isInProgress)
                    .filter(m -> m.getPlayoffStage() != null)
                    .map(AmericanoMatch::getId)
                    .toList();
            if (inProgressIds.isEmpty()) {
                break;
            }
            for (Long matchId : inProgressIds) {
                playoffService.submitPlayoffResult(matchId, 6, 3);
            }
        }
    }

    /**
     * Если в итоговой сетке плей-офф встретились две команды, уже игравшие друг с другом
     * в квалификации, это допустимо только как задокументированное исключение — в логах
     * должно быть явное предупреждение о неизбежном/непредотвращённом рематче.
     */
    private void assertNoUnexplainedRematchesInPlayoff(Long tournamentId) {
        Map<Long, Set<Long>> qualOpponents = buildOpponentsMap(
                matchRepository.findByTournamentIdAndPhase(tournamentId, TournamentPhase.QUALIFICATION));

        List<AmericanoMatch> playoffMatches = matchRepository.findByTournamentIdAndPhase(tournamentId, TournamentPhase.PLAYOFF)
                .stream()
                .filter(m -> m.getTeam1Id() != null && m.getTeam2Id() != null)
                .toList();

        boolean anyRematch = playoffMatches.stream()
                .anyMatch(m -> qualOpponents.getOrDefault(m.getTeam1Id(), Set.of()).contains(m.getTeam2Id()));

        if (anyRematch) {
            boolean warned = logAppender.list.stream()
                    .anyMatch(event -> event.getLevel().toString().equals("WARN")
                            && (event.getFormattedMessage().contains("revancha") || event.getFormattedMessage().contains("already played")));
            assertThat(warned)
                    .as("a rematch occurred in the playoff bracket without a corresponding WARN log")
                    .isTrue();
        }
    }

    private void assertChampionAndRunnerUpDetermined(Long tournamentId) {
        List<AmericanoTeam> finalTeams = teamRepository.findByTournamentId(tournamentId);

        long champions = finalTeams.stream().filter(t -> Integer.valueOf(1).equals(t.getCurrentPosition())).count();
        long runnersUp = finalTeams.stream().filter(t -> Integer.valueOf(2).equals(t.getCurrentPosition())).count();
        assertThat(champions).as("exactly one champion").isEqualTo(1);
        assertThat(runnersUp).as("exactly one runner-up").isEqualTo(1);

        Tournament tournament = tournamentRepository.findById(tournamentId).orElseThrow();
        assertThat(tournament.getEstado()).isEqualTo(TournamentStatus.FINALIZADO);
    }

    /**
     * Колонка «Ситуация» показывает не просто общий статус «Выбыла», а этап плей-офф,
     * на котором команда закончила турнир — проверяет, что {@code eliminatedStage} в DTO совпадает
     * с этапом последнего сыгранного командой матча плей-офф и является реальным довфинальным этапом.
     */
    private void assertEliminatedTeamsExposeCorrectStage(Long tournamentId, List<AmericanoTeam> teams) {
        List<AmericanoTeamDto> ranking = playoffService.getQualRanking(tournamentId).getRanking();
        List<AmericanoMatch> playoffMatches = matchRepository.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId)
                .stream()
                .filter(m -> m.getPlayoffStage() != null)
                .toList();

        boolean anyEliminated = false;
        for (AmericanoTeam team : teams) {
            AmericanoTeamDto dto = ranking.stream()
                    .filter(t -> t.getId().equals(team.getId()))
                    .findFirst().orElseThrow();
            if (!"ELIMINATED".equals(dto.getTournamentStatus())) {
                continue;
            }
            anyEliminated = true;

            List<AmericanoMatch> teamPlayoffMatches = playoffMatches.stream()
                    .filter(m -> team.getId().equals(m.getTeam1Id()) || team.getId().equals(m.getTeam2Id()))
                    .toList();
            AmericanoMatch lastMatch = teamPlayoffMatches.get(teamPlayoffMatches.size() - 1);

            assertThat(dto.getEliminatedStage())
                    .as("eliminatedStage for team %d matches its last playoff match stage", team.getId())
                    .isEqualTo(lastMatch.getPlayoffStage().name());
            assertThat(dto.getEliminatedStage())
                    .as("eliminatedStage for team %d is a real pre-final playoff stage", team.getId())
                    .isIn("ROUND_OF_16", "QUARTER_FINAL", "SEMI_FINAL");
        }
        assertThat(anyEliminated).as("at least one team was eliminated in this bracket size").isTrue();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // УТИЛИТЫ
    // ═══════════════════════════════════════════════════════════════════════

    private Map<Long, Long> countCompletedMatchesByTeam(List<AmericanoMatch> matches) {
        Map<Long, Long> result = new HashMap<>();
        for (AmericanoMatch m : matches) {
            if (!m.isCompleted()) continue;
            result.merge(m.getTeam1Id(), 1L, Long::sum);
            result.merge(m.getTeam2Id(), 1L, Long::sum);
        }
        return result;
    }

    private Map<Long, Set<Long>> buildOpponentsMap(List<AmericanoMatch> matches) {
        Map<Long, Set<Long>> opponents = new HashMap<>();
        for (AmericanoMatch m : matches) {
            if (!m.isCompleted() || m.getTeam1Id() == null || m.getTeam2Id() == null) continue;
            opponents.computeIfAbsent(m.getTeam1Id(), k -> new HashSet<>()).add(m.getTeam2Id());
            opponents.computeIfAbsent(m.getTeam2Id(), k -> new HashSet<>()).add(m.getTeam1Id());
        }
        return opponents;
    }

    private String pairKey(Long a, Long b) {
        return a < b ? a + ":" + b : b + ":" + a;
    }
}
