package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffTeamRequest;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.PlayoffStage;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-465: формирование первого этапа плей-офф до полного завершения квалификации
 * (только степень двойки команд — 8/16, без play-in, см. спеку "Вне скоупа").
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

    @Test
    void canInitPlayoff_falseUntilAtLeastTwoTeamsSettled() {
        Long tournamentId = createTournament(8);
        List<AmericanoTeam> teams = createTeams(tournamentId, 8);

        assertThat(playoffService.canInitPlayoff(tournamentId)).isFalse();

        playoffService.initQualification(tournamentId, 8);
        assertThat(playoffService.canInitPlayoff(tournamentId))
                .as("qualification started but nobody finished 2 matches yet")
                .isFalse();

        completeFullQualificationFor(tournamentId, teams.get(0), teams.get(1));
        assertThat(playoffService.canInitPlayoff(tournamentId))
                .as("one settled pair is enough")
                .isTrue();
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

    @Test
    void earlyInitPlayoff_eightTeams_createsPartialBracketThenBackfillsAfterQualificationCompletes() {
        Long tournamentId = createTournament(8);
        List<AmericanoTeam> teams = createTeams(tournamentId, 8);
        playoffService.initQualification(tournamentId, 8);

        // 6 из 8 команд полностью отыграли квалификацию (3 готовые пары), 2 команды ещё
        // не закончили (по 1 матчу).
        completeFullQualificationFor(tournamentId, teams.get(0), teams.get(1));
        completeFullQualificationFor(tournamentId, teams.get(2), teams.get(3));
        completeFullQualificationFor(tournamentId, teams.get(4), teams.get(5));
        AmericanoMatch pendingFirstMatch = playoffService.createQualificationMatch(
                tournamentId, teams.get(6).getId(), teams.get(7).getId(), 4);
        playoffService.submitQualResult(pendingFirstMatch.getId(), 6, 2);

        assertThat(playoffService.canInitPlayoff(tournamentId)).isTrue();
        playoffService.initPlayoff(tournamentId);

        List<AmericanoMatch> qfMatches = matchRepository.findByTournamentIdAndPlayoffStage(tournamentId, PlayoffStage.QUARTER_FINAL);
        assertThat(qfMatches).hasSize(4);

        long realMatches = qfMatches.stream().filter(m -> m.getTeam1Id() != null && m.getTeam2Id() != null).count();
        long tbdMatches = qfMatches.stream().filter(m -> m.getTeam1Id() == null && m.getTeam2Id() == null).count();
        assertThat(realMatches).as("3 pairs formed from the 6 settled teams").isEqualTo(3);
        assertThat(tbdMatches).as("1 TBD slot left for the 2 not-yet-settled teams").isEqualTo(1);
        qfMatches.stream().filter(m -> m.getTeam1Id() == null).forEach(m ->
                assertThat(m.getNote()).isEqualTo("TBD vs TBD"));

        // Следующие стадии созданы на полный размер (2 SF + 1 Final), как и при обычном посеве.
        assertThat(matchRepository.findByTournamentIdAndPlayoffStage(tournamentId, PlayoffStage.SEMI_FINAL)).hasSize(2);
        assertThat(matchRepository.findByTournamentIdAndPlayoffStage(tournamentId, PlayoffStage.FINAL)).hasSize(1);

        // Команда 6 доигрывает квалификацию последней — это должно автоматически дозаполнить TBD-слот.
        AmericanoMatch secondMatchTeam6 = playoffService.createQualificationMatch(
                tournamentId, teams.get(6).getId(), teams.get(7).getId(), 4);
        playoffService.submitQualResult(secondMatchTeam6.getId(), 6, 4);

        List<AmericanoMatch> qfAfterBackfill = matchRepository.findByTournamentIdAndPlayoffStage(tournamentId, PlayoffStage.QUARTER_FINAL);
        long realAfterBackfill = qfAfterBackfill.stream().filter(m -> m.getTeam1Id() != null && m.getTeam2Id() != null).count();
        assertThat(realAfterBackfill).as("backfill should seed the last pending slot").isEqualTo(4);

        List<Long> seededTeamIds = qfAfterBackfill.stream()
                .flatMap(m -> java.util.stream.Stream.of(m.getTeam1Id(), m.getTeam2Id()))
                .toList();
        assertThat(seededTeamIds).contains(teams.get(6).getId(), teams.get(7).getId());

        // Регрессия: уже созданные (реальные) матчи первого этапа не были пересобраны.
        List<AmericanoMatch> stillSamePairs = qfAfterBackfill.stream()
                .filter(m -> !m.getTeam1Id().equals(teams.get(6).getId()) && !m.getTeam2Id().equals(teams.get(6).getId())
                        && !m.getTeam1Id().equals(teams.get(7).getId()) && !m.getTeam2Id().equals(teams.get(7).getId()))
                .toList();
        assertThat(stillSamePairs).hasSize(3);

        // Турнир продолжается по стандартной сетке до конца.
        playThroughPlayoff(tournamentId);
        Tournament tournament = tournamentRepository.findById(tournamentId).orElseThrow();
        assertThat(tournament.getEstado()).isEqualTo(TournamentStatus.FINALIZADO);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SETUP / HELPERS
    // ═══════════════════════════════════════════════════════════════════════

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
}
