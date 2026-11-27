package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.dto.americano.*;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.padelcoreservice.repository.americano.*;
import com.padle.core.padelcoreservice.service.conduct.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.test", "spring.mail.password=test",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test", "app.scheduling.enabled=false"
})
@Transactional
class RoundRobinIntegrationTest {
    @Autowired TeamAmericanoService service;
    @Autowired RoundRobinAdditionalMatches additional;
    @Autowired TeamPlayoffService playoff;
    @Autowired TournamentRepository tournaments;
    @Autowired ClubRepository clubs;
    @Autowired PlayerRepository players;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired AmericanoRoundRepository rounds;
    @Autowired AmericanoMatchRepository matches;
    @Autowired TournamentConductHistory history;
    @Autowired EntityManager em;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;

    @ParameterizedTest
    @EnumSource(RoundRobinScoringMode.class)
    void fullCircleFinalBronzeCorrectionAndRestore(RoundRobinScoringMode mode) {
        var id = fixture(5);
        service.initialize(id, config(mode));
        assertThat(service.getRanking(id).getScoringMode()).isEqualTo(mode);
        assertThat(matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(id)).hasSize(10);
        var initial = service.getRanking(id).getRanking();
        var finalRequest = request(initial.get(4).getId(), initial.get(3).getId(), 1, 1);
        assertThatThrownBy(() -> additional.add(id, finalRequest)).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(() -> service.finishTournament(id)).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(() -> playoff.initQualification(id, 2)).isInstanceOf(InvalidStateException.class);
        scoreCircle(id, mode);
        var before = service.getRanking(id).getRanking();
        var first = additional.add(id, finalRequest);
        assertThat(additional.add(id, finalRequest).getId()).isEqualTo(first.getId());
        assertThatThrownBy(() -> additional.add(id, request(initial.get(0).getId(), initial.get(1).getId(), 1, 3)))
                .isInstanceOf(InvalidStateException.class);
        var bronze = additional.add(id, request(initial.get(0).getId(), initial.get(1).getId(), 2, 3));
        assertThatThrownBy(() -> service.finishTournament(id)).isInstanceOf(InvalidStateException.class);
        score(first.getId(), mode, true);
        score(bronze.getId(), mode, false);
        var state = service.getRanking(id);
        assertThat(state.getRanking()).usingRecursiveComparison().isEqualTo(before);
        assertThat(state.getFinalRanking().get(0).teamId()).isEqualTo(initial.get(4).getId());
        assertThat(state.getFinalRanking().get(2).teamId()).isEqualTo(initial.get(1).getId());
        assertThat(state.getCompletedRounds()).isEqualTo(state.getTotalRounds());
        var owner = Owner.builder().id(99999L).role(OwnerRole.SUPER_ADMIN).build();
        var version = history.list(id, owner).get(0).id();
        score(first.getId(), mode, false);
        assertThat(service.getRanking(id).getFinalRanking().get(0).teamId()).isEqualTo(initial.get(3).getId());
        em.flush(); em.clear();
        var preview = history.preview(id, version, owner);
        history.restore(id, version, preview.latestVersion(), owner);
        em.flush(); em.clear();
        assertThat(service.getRanking(id).getFinalRanking().get(0).teamId()).isEqualTo(initial.get(4).getId());
        assertThat(service.getRanking(id).getScoringMode()).isEqualTo(mode);
        service.finishTournament(id);
        assertThat(tournaments.findById(id).orElseThrow().getEstado()).isEqualTo(TournamentStatus.FINALIZADO);
    }

    @ParameterizedTest
    @EnumSource(RoundRobinScoringMode.class)
    void evenFieldCanFinishWithoutAnyAdditionalMatches(RoundRobinScoringMode mode) {
        var id = fixture(4);
        service.initialize(id, config(mode));
        scoreCircle(id, mode);
        var finished = service.finishTournament(id);
        assertThat(finished.getAdditionalMatches()).isEmpty();
        assertThat(finished.getFinalRanking().stream().map(RoundRobinStandingDto::teamId).toList())
                .containsExactlyElementsOf(finished.getRanking().stream().map(AmericanoTeamDto::getId).toList());
        assertThatThrownBy(() -> service.initialize(id, config(mode))).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void legacyNullScoringIsPointsAndBadSettingsDoNotCreateRounds() {
        var id = fixture(2);
        var config = config(RoundRobinScoringMode.POINTS);
        config.setCourts(0);
        assertThatThrownBy(() -> service.initialize(id, config)).isInstanceOf(InvalidStateException.class);
        assertThat(rounds.findByTournamentIdOrderByRoundNumberAsc(id)).isEmpty();
        service.initialize(id, config(RoundRobinScoringMode.POINTS));
        rounds.findByTournamentIdOrderByRoundNumberAsc(id).forEach(r -> r.setScoringMode(null));
        scoreCircle(id, RoundRobinScoringMode.POINTS);
        assertThat(service.getRanking(id).getScoringMode()).isEqualTo(RoundRobinScoringMode.POINTS);
    }

    @Test
    void additionalActionsRequireOwnerCsrfAndValidTeams() throws Exception {
        var id = fixture(4);
        service.initialize(id, config(RoundRobinScoringMode.POINTS));
        scoreCircle(id, RoundRobinScoringMode.POINTS);
        var ranking = service.getRanking(id).getRanking();
        var request = request(ranking.get(0).getId(), ranking.get(1).getId(), 1, 1);
        var url = "/tournaments/team-americano/api/round-robin/" + id + "/additional";
        var payload = "{\"team1Id\":%d,\"team2Id\":%d,\"courtNumber\":1,\"placementStart\":1,\"requestId\":\"%s\"}"
                .formatted(request.team1Id(), request.team2Id(), request.requestId());
        var superadmin = Owner.builder().id(90000L).firstName("QA").role(OwnerRole.SUPER_ADMIN).build();
        var foreign = Owner.builder().id(90001L).firstName("QA").role(OwnerRole.CLUB_ADMIN).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(superadmin))
                .accept("application/json").contentType("application/json").content(payload))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(foreign))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .accept("application/json").contentType("application/json").content(payload))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(superadmin))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .accept("application/json").contentType("application/json").content(payload))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(additional.list(id)).hasSize(1);
    }

    @Test
    void tiedTeamsUseTheirSavedMeetingAndCorrectionIsReversible() {
        var id = fixture(4);
        service.initialize(id, config(RoundRobinScoringMode.SETS));
        var initial = service.getRanking(id).getRanking();
        var a = initial.get(0).getId(); var b = initial.get(1).getId();
        var c = initial.get(2).getId(); var d = initial.get(3).getId();
        scorePair(id, a, b, 6, 4); scorePair(id, a, c, 6, 4); scorePair(id, d, a, 6, 2);
        scorePair(id, b, c, 6, 5); scorePair(id, b, d, 6, 5); scorePair(id, c, d, 6, 4);
        assertThat(service.getRanking(id).getRanking().stream().map(AmericanoTeamDto::getId))
                .containsExactly(a, b, d, c);
        var owner = Owner.builder().id(99999L).role(OwnerRole.SUPER_ADMIN).build();
        var version = history.list(id, owner).get(0).id();
        scorePair(id, a, b, 4, 6);
        assertThat(service.getRanking(id).getRanking().get(0).getId()).isEqualTo(b);
        em.flush(); em.clear();
        var preview = history.preview(id, version, owner);
        history.restore(id, version, preview.latestVersion(), owner);
        em.flush(); em.clear();
        var finished = service.finishTournament(id);
        assertThat(finished.getRanking().stream().map(AmericanoTeamDto::getId)).containsExactly(a, b, d, c);
        assertThat(finished.getFinalRanking().stream().map(RoundRobinStandingDto::position)).containsExactly(1, 2, 3, 4);
    }

    private void scorePair(Long id, Long first, Long second, int score1, int score2) {
        var match = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(id).stream()
                .filter(m -> java.util.Set.of(first, second).equals(java.util.Set.of(m.getTeam1Id(), m.getTeam2Id())))
                .findFirst().orElseThrow();
        boolean sameOrder = first.equals(match.getTeam1Id());
        service.submitMatchResult(match.getId(), sameOrder ? score1 : score2, sameOrder ? score2 : score1);
    }

    private void scoreCircle(Long id, RoundRobinScoringMode mode) {
        matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(id).stream()
                .filter(m -> m.getRound().getPhase() == null).forEach(m -> score(m.getId(), mode, true));
    }

    private void score(Long id, RoundRobinScoringMode mode, boolean first) {
        int winner = mode == RoundRobinScoringMode.SETS ? 6 : 18;
        int loser = mode == RoundRobinScoringMode.SETS ? 4 : 14;
        service.submitMatchResult(id, first ? winner : loser, first ? loser : winner);
    }

    private TeamAmericanoConfigDto config(RoundRobinScoringMode mode) {
        var config = new TeamAmericanoConfigDto();
        config.setScoringMode(mode); config.setCourts(2); config.setPointsPerMatch(32);
        return config;
    }

    private RoundRobinAdditionalMatchRequest request(Long first, Long second, int court, int place) {
        return new RoundRobinAdditionalMatchRequest(first, second, court, place, "QA", UUID.randomUUID());
    }

    private Long fixture(int count) {
        var club = clubs.save(Club.builder().nombre("RR QA").isActive(true).build());
        var tournament = tournaments.save(Tournament.builder().clubId(club.getId()).nombre("RR QA")
                .fechaInicio(LocalDate.now().plusDays(2)).horaInicio(LocalTime.NOON).generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7).tipo(TournamentType.AMERICANO_TEAMS).modalidad(Modalidad.DOBLES)
                .cupoMax(count).precio(BigDecimal.ZERO).estado(TournamentStatus.CERRADO)
                .contactoOrganizador("qa@example.test").isActive(true).build());
        for (int i = 0; i < count; i++) {
            var first = player(); var second = player();
            for (var p : List.of(first, second)) registrations.save(TournamentRegistration.builder()
                    .tournament(tournament).player(p).status(RegistrationStatus.CONFIRMED).isDoubleRegistration(true)
                    .mainPlayerId(first.getId()).partner(p == first ? second : first).position(i + 1).build());
        }
        em.flush();
        return tournament.getId();
    }

    private PlayerPadel player() {
        var suffix = UUID.randomUUID().toString();
        return players.save(PlayerPadel.builder().nombre("QA").apellido(suffix.substring(0, 6))
                .email(suffix + "@example.test").passwordHash("test").activo(true).emailConfirmado(true)
                .oauth2User(false).preferredLocale("es").nivelJugador(Nivel.C7).build());
    }
}
