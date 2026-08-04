package com.padle.core.padelcoreservice.service.americano;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchSuggestionDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffTeamRequest;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.PlayoffStage;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T17: сквозные regression-тесты полного цикла Team Playoff (квалификация → плей-офф → финал)
 * для каждого N от 8 до 16 — весь диапазон, поддержанный таблицей ТЗ §4 (T13-T16).
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

        playThroughPlayoff(tournamentId);
        assertNoUnexplainedRematchesInPlayoff(tournamentId);
        assertChampionAndRunnerUpDetermined(tournamentId);
    }

    /**
     * #271: команды приходят не одновременно — квалификация должна стартовать всего от 2 команд,
     * не дожидаясь полного набора на четвертьфинал. Плей-офф здесь намеренно не проверяется —
     * он по-прежнему требует достаточного числа команд и стартует отдельной кнопкой позже.
     */
    @ParameterizedTest(name = "{0} equipos (solo calificación)")
    @ValueSource(ints = {2, 3})
    void qualificationStartsWithFewerThanFourTeams(int teamCount) {
        Long tournamentId = createTournament(teamCount);
        List<AmericanoTeam> teams = createTeams(tournamentId, teamCount);

        runQualification(tournamentId, teams);

        assertEachTeamPlayedExactlyTwoQualMatches(tournamentId, teams);
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
                .categoriaNivel(com.padle.core.padelcoreservice.model.enums.Nivel.C7)
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

            TeamPlayoffTeamRequest req = new TeamPlayoffTeamRequest();
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
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // КВАЛИФИКАЦИЯ
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Прогоняет квалификацию через реальный court-driven механизм: сначала попарно
     * назначает всех "нетронутых" команд (оставляя, при нечётном N, одну "дополнительную" —
     * T14), затем следует предложениям {@link TeamPlayoffService#suggestSecondRoundPairings}
     * (T5/T14/T15) до тех пор, пока каждая команда не сыграет ровно 2 матча.
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

    /** Структурная гарантия T5/T14/T15: победитель-к-победителю/проигравший-к-проигравшему не может повторить пару 1-го тура. */
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

    /** ТЗ §4 (T13): проверяет, что число напрямую посеянных команд и play-in-матчей точно соответствует таблице. */
    private void assertQuarterFinalSeedingMatchesSpecTable(Long tournamentId, int teamCount) {
        List<AmericanoMatch> qfMatches = matchRepository.findByTournamentIdAndPlayoffStage(tournamentId, PlayoffStage.QUARTER_FINAL);
        assertThat(qfMatches).hasSize(4);

        long directTeamsSeeded = qfMatches.stream()
                .flatMap(m -> java.util.stream.Stream.of(m.getTeam1Id(), m.getTeam2Id()))
                .filter(java.util.Objects::nonNull)
                .count();
        assertThat(directTeamsSeeded).isEqualTo(PlayoffFormat.directToQuarterFinal(teamCount));

        List<AmericanoMatch> playInMatches = matchRepository.findByTournamentIdAndPlayoffStage(tournamentId, PlayoffStage.ROUND_OF_16);
        assertThat(playInMatches).hasSize(PlayoffFormat.playInTeams(teamCount) / 2);
    }

    /** Раз за разом доигрывает все текущие IN_PROGRESS матчи плей-офф (play-in → QF → SF → Final), пока турнир не завершится. */
    private void playThroughPlayoff(Long tournamentId) {
        int guard = 0;
        while (guard++ < 20) {
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
     * ТЗ §1/§36 (T13/T16): если в итоговой сетке плей-офф встретились две команды, уже
     * игравшие друг с другом в квалификации, это допустимо только как задокументированное
     * исключение — в логах должно быть явное предупреждение о неизбежном/непредотвращённом рематче.
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
