package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.dto.americano.*;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import com.padle.core.padelcoreservice.service.americano.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {"spring.mail.username=test@example.test", "spring.mail.password=test",
        "spring.security.oauth2.client.registration.google.client-id=test", "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test", "app.scheduling.enabled=false"})
@Transactional
class RoundRobinEloIntegrationTest {
    @Autowired TeamAmericanoService service;
    @Autowired RoundRobinAdditionalMatches additional;
    @Autowired TeamPlayoffService playoff;
    @Autowired KingOfCourtService king;
    @Autowired AmericanoService americano;
    @Autowired TournamentRepository tournaments;
    @Autowired ClubRepository clubs;
    @Autowired PlayerRepository players;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired AmericanoMatchRepository matches;
    @Autowired JdbcClient jdbc;
    @Autowired EntityManager em;

    @ParameterizedTest
    @EnumSource(RoundRobinScoringMode.class)
    void savedCircleAndPlacementMatchesReplayAndScoreCorrectionsAreReflected(RoundRobinScoringMode mode) {
        var club = clubs.save(Club.builder().nombre("Elo RR QA").isActive(true).build());
        var tournament = tournaments.save(Tournament.builder().clubId(club.getId()).nombre("Elo RR QA")
                .fechaInicio(LocalDate.now().plusDays(2)).horaInicio(LocalTime.NOON).generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7).tipo(TournamentType.AMERICANO_TEAMS).modalidad(Modalidad.DOBLES)
                .cupoMax(4).precio(BigDecimal.ZERO).estado(TournamentStatus.CERRADO)
                .contactoOrganizador("qa@example.test").isActive(true).build());
        for (int i = 0; i < 4; i++) {
            var first = player(); var second = player();
            for (var p : List.of(first, second)) registrations.save(TournamentRegistration.builder()
                    .tournament(tournament).player(p).status(RegistrationStatus.CONFIRMED).isDoubleRegistration(true)
                    .mainPlayerId(first.getId()).partner(p == first ? second : first).position(i + 1).build());
        }
        em.flush();
        var config = new TeamAmericanoConfigDto();
        config.setScoringMode(mode); config.setCourts(2); config.setPointsPerMatch(32);
        service.initialize(tournament.getId(), config);
        var circle = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournament.getId());
        assertThat(circle).hasSize(6);
        int winner = mode == RoundRobinScoringMode.SETS ? 6 : 18;
        int loser = mode == RoundRobinScoringMode.SETS ? 4 : 14;
        service.submitMatchResult(circle.get(0).getId(), winner, loser);
        em.flush();
        var live = new PreviewEloLiveEvents(jdbc, king, americano, playoff);
        var event = live.load(LocalDate.now()).events().stream().filter(e -> e.key().equals("S-" + tournament.getId())).findFirst().orElseThrow();
        assertThat(event.format()).isEqualTo("pair");
        assertThat(event.resultsUrl()).isEqualTo("/tournaments/team-americano/" + tournament.getId());
        assertThat(event.games()).hasSize(1);
        assertThat(event.games().get(0).left()).isEqualTo(winner);
        assertThat(event.games().get(0).right()).isEqualTo(loser);
        assertThat(live.load(LocalDate.now(), Set.of(), Set.of("S-" + tournament.getId())).events())
                .noneMatch(e -> e.key().equals("S-" + tournament.getId()));
        circle.forEach(m -> service.submitMatchResult(m.getId(), winner, loser));
        var rankings = service.getRanking(tournament.getId()).getRanking();
        var extra = additional.add(tournament.getId(), new RoundRobinAdditionalMatchRequest(
                rankings.get(0).getId(), rankings.get(1).getId(), 1, 1, "Final", UUID.randomUUID()));
        service.submitMatchResult(extra.getId(), winner, loser);
        service.finishTournament(tournament.getId());
        em.flush();
        var complete = live.load(LocalDate.now()).events().stream().filter(e -> e.key().equals("S-" + tournament.getId())).findFirst().orElseThrow();
        assertThat(complete.games()).hasSize(7);
        assertThat(complete.games()).filteredOn(PreviewEloReplay.Game::verifiedFinal).hasSize(1);
        assertThat(complete.games()).extracting(PreviewEloReplay.Game::batch).contains(1, 2, 3, 4);
        var corrected = circle.get(0);
        service.submitMatchResult(corrected.getId(), loser, winner);
        em.flush();
        var changed = live.load(LocalDate.now()).events().stream().filter(e -> e.key().equals("S-" + tournament.getId())).findFirst().orElseThrow();
        assertThat(changed.games()).filteredOn(g -> g.key().equals("pair:" + corrected.getId()))
                .singleElement().satisfies(g -> {assertThat(g.left()).isEqualTo(loser); assertThat(g.right()).isEqualTo(winner);});
        var before = new PreviewEloReplay(40, true).replay(LocalDate.now(), live.load(LocalDate.now()).names(), List.of(complete));
        var after = new PreviewEloReplay(40, true).replay(LocalDate.now(), live.load(LocalDate.now()).names(), List.of(changed));
        assertThat(after).isNotEqualTo(before);
    }

    private PlayerPadel player() {
        var suffix = UUID.randomUUID().toString();
        return players.save(PlayerPadel.builder().nombre("QA").apellido(suffix.substring(0, 6))
                .email(suffix + "@example.test").passwordHash("test").activo(true).emailConfirmado(true)
                .oauth2User(false).preferredLocale("es").nivelJugador(Nivel.C7).build());
    }
}
