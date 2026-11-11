package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.mail.username=test@example.test", "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-secret",
        "recaptcha.site-key=test-site", "recaptcha.secret-key=test-secret"
})
class HistoricalParticipantIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private ClubRepository clubs;
    @Autowired private TournamentRepository tournaments;
    @Autowired private PlayerRepository players;
    @Autowired private AmericanoRoundRepository rounds;
    @Autowired private AmericanoTeamRepository teams;
    @Autowired private AmericanoMatchRepository matches;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcClient jdbc;

    @Test
    void linksExistingPlayerAndCreatesGuestWithoutChangingResultOrAddingRegistration() throws Exception {
        Tournament tournament = tournament();
        PlayerPadel existing = player("Adrián", "Known");
        AmericanoMatch match = finalMatch(tournament, existing.getNombreCompleto(), "Camila Archive");
        Long matchId = match.getId();
        Long team1 = match.getTeam1Id();
        Long team2 = match.getTeam2Id();
        String adminPage = "/tournaments/team-playoff/admin/" + tournament.getId();
        String base = "/tournaments/team-playoff/admin/matches/" + matchId + "/participant/";
        var admin = auth(OwnerRole.SUPER_ADMIN, null);

        mvc.perform(get(adminPage).with(admin))
                .andExpect(status().isOk()).andExpect(content().string(containsString(base + "1")));
        mvc.perform(get(base + "1").with(admin).locale(Locale.ENGLISH))
                .andExpect(status().isOk()).andExpect(content().string(containsString("6:5")))
                .andExpect(content().string(containsString("Adrián Known · #" + existing.getId())));
        mvc.perform(get(base + "1").with(auth(OwnerRole.CLUB_ADMIN, -1L)))
                .andExpect(redirectedUrl("/error/403"));
        mvc.perform(post(base + "1").with(admin).param("expectedName", "Adrián Known")
                        .param("playerId", existing.getId().toString()))
                .andExpect(redirectedUrl("/error/403"));
        mvc.perform(post(base + "1").with(admin).with(csrf()).locale(Locale.ENGLISH)
                        .param("expectedName", "Adrián Known").param("playerId", existing.getId().toString()))
                .andExpect(redirectedUrl(adminPage + "#pmatch-" + matchId));
        mvc.perform(post(base + "2").with(admin).with(csrf()).locale(Locale.ENGLISH)
                        .param("expectedName", "Camila Archive").param("firstName", "ADRIAN")
                        .param("lastName", "Known"))
                .andExpect(redirectedUrl(base + "2"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post(base + "2").with(admin).with(csrf()).locale(Locale.ENGLISH)
                        .param("expectedName", "Camila Archive").param("firstName", "Camila")
                        .param("lastName", "Archive"))
                .andExpect(redirectedUrl(adminPage + "#pmatch-" + matchId));
        mvc.perform(post(base + "2").with(admin).with(csrf()).locale(Locale.ENGLISH)
                        .param("expectedName", "Camila Archive").param("firstName", "Camila")
                        .param("lastName", "Archive"))
                .andExpect(redirectedUrl(base + "2")).andExpect(flash().attributeExists("error"));

        entityManager.flush();
        entityManager.clear();
        AmericanoMatch saved = matches.findById(matchId).orElseThrow();
        assertThat(saved.getTeam1Player2().getId()).isEqualTo(existing.getId());
        assertThat(saved.getTeam2Player2().getNombreCompleto()).isEqualTo("Camila Archive");
        assertThat(saved.getTeam2Player2().isGuestAccount()).isTrue();
        assertThat(saved.getTeam1Id()).isEqualTo(team1);
        assertThat(saved.getTeam2Id()).isEqualTo(team2);
        assertThat(saved.getTeam1Games()).isEqualTo(6);
        assertThat(saved.getTeam2Games()).isEqualTo(5);
        assertThat(saved.getPlayoffStage()).isEqualTo(PlayoffStage.FINAL);
        assertThat(saved.isCompleted()).isTrue();
        assertThat(teams.findById(team2).orElseThrow().getPlayer2()).isNull();
        assertThat(jdbc.sql("SELECT count(*) FROM tournament_registrations_db WHERE tournament_id = :id")
                .param("id", tournament.getId()).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM tournament_conduct_history WHERE tournament_id = :id AND action = 'MATCH_PARTICIPANT'")
                .param("id", tournament.getId()).query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM player_padel_db WHERE nombre = 'Camila' AND apellido = 'Archive'")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void standaloneGuestRequiresSuperAdminAndCreatesOnlyOneProfile() throws Exception {
        String path = "/admin/players/guests";
        var admin = auth(OwnerRole.SUPER_ADMIN, null);
        var clubAdmin = auth(OwnerRole.CLUB_ADMIN, -1L);
        long registrationsBefore = jdbc.sql("SELECT count(*) FROM tournament_registrations_db")
                .query(Long.class).single();

        mvc.perform(post(path).with(clubAdmin).with(csrf()).param("firstName", "Rosa"))
                .andExpect(redirectedUrl("/error/403"));
        mvc.perform(post(path).with(admin).param("firstName", "Rosa"))
                .andExpect(redirectedUrl("/error/403"));

        var response = mvc.perform(post(path).with(admin).with(csrf()).locale(Locale.ENGLISH)
                        .param("firstName", "Rosa").param("lastName", "Standalone Archive"))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse();
        String profileUrl = response.getRedirectedUrl();
        assertThat(profileUrl).matches("/admin/players/\\d+");
        long id = Long.parseLong(profileUrl.substring("/admin/players/".length()));
        PlayerPadel guest = players.findById(id).orElseThrow();
        assertThat(PlayerPadel.isGuestEmail(guest.getEmail())).isTrue();
        assertThat(guest.getTelefono()).isNull();

        mvc.perform(post(path).with(admin).with(csrf()).locale(Locale.ENGLISH)
                        .param("firstName", "ROSA").param("lastName", "Standalone Archive"))
                .andExpect(redirectedUrl("/admin/players")).andExpect(flash().attributeExists("guestError"));
        assertThat(jdbc.sql("SELECT count(*) FROM player_padel_db WHERE apellido = 'Standalone Archive'")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM tournament_registrations_db")
                .query(Long.class).single()).isEqualTo(registrationsBefore);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor auth(OwnerRole role, Long clubId) {
        Owner owner = Owner.builder().id(99001L).firstName("Admin").role(role).clubId(clubId).build();
        return authentication(new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
    }

    private Tournament tournament() {
        Club club = clubs.save(Club.builder().nombre("Historical participant QA").isActive(true).build());
        return tournaments.save(Tournament.builder().clubId(club.getId()).nombre("Historical final QA")
                .fechaInicio(LocalDate.now().minusDays(2)).horaInicio(LocalTime.NOON).generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7).tipo(TournamentType.AMERICANO_TEAMS).modalidad(Modalidad.DOBLES)
                .cupoMax(8).precio(BigDecimal.ZERO).estado(TournamentStatus.FINALIZADO)
                .contactoOrganizador("qa@example.test").isActive(true).build());
    }

    private AmericanoMatch finalMatch(Tournament tournament, String first, String second) {
        AmericanoRound round = rounds.save(AmericanoRound.builder().tournament(tournament).roundNumber(1)
                .status(AmericanoRoundStatus.COMPLETED).phase(TournamentPhase.PLAYOFF).isDoubles(true)
                .pointsPerMatch(1).courts(1).note("FINAL").build());
        AmericanoTeam team1 = teams.save(AmericanoTeam.builder().tournament(tournament).teamNumber(1)
                .player1(player("First", "Captain")).player2Name(first).build());
        AmericanoTeam team2 = teams.save(AmericanoTeam.builder().tournament(tournament).teamNumber(2)
                .player1(player("Second", "Captain")).player2Name(second).build());
        return matches.saveAndFlush(AmericanoMatch.builder().tournament(tournament).round(round).matchNumber(1)
                .courtNumber(1).team1Id(team1.getId()).team2Id(team2.getId()).team1Player1(team1.getPlayer1())
                .team2Player1(team2.getPlayer1()).status(AmericanoRoundStatus.COMPLETED).isDoubles(true)
                .playoffStage(PlayoffStage.FINAL).team1Games(6).team2Games(5).team1Score(1).team2Score(0).build());
    }

    private PlayerPadel player(String first, String last) {
        return players.save(PlayerPadel.builder().nombre(first).apellido(last).email(UUID.randomUUID() + "@example.test")
                .passwordHash("$2b$12$test").activo(true).emailConfirmado(true).oauth2User(false)
                .preferredLocale("es").nivelJugador(Nivel.C7).build());
    }
}
