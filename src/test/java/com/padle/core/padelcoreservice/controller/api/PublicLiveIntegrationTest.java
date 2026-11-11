package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.live.PublicLiveDtos.*;
import com.padle.core.padelcoreservice.dto.americano.RankingCriteria;
import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffAddTeamRequest;
import org.hibernate.stat.Statistics;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.americano.*;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.repository.americano.*;
import com.padle.core.padelcoreservice.service.*;
import com.padle.core.padelcoreservice.service.americano.*;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"spring.jpa.properties.hibernate.generate_statistics=true", "app.base-url=https://public.example.test"})
class PublicLiveIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired PublicLiveService live;
    @Autowired TournamentRepository events;
    @Autowired ClubRepository clubs;
    @Autowired PlayerRepository players;
    @Autowired TournamentKingOfCourtRepository kings;
    @Autowired KingOfCourtPlayerStatsRepository kingStats;
    @Autowired KingOfCourtRoundRepository kingRounds;
    @Autowired KingOfCourtCourtRepository courts;
    @Autowired AmericanoRoundRepository rounds;
    @Autowired AmericanoPlayerRepository americanoPlayers;
    @Autowired AmericanoMatchRepository matches;
    @Autowired KingOfCourtService kingService;
    @Autowired AmericanoService americano;
    @Autowired TeamPlayoffService playoff;

    @ParameterizedTest
    @EnumSource(value=TournamentType.class, names={"KING_OF_COURT", "AMERICANO", "AMERICANO_TEAMS"})
    void uninitializedReadIsPublicAndDoesNotWrite(TournamentType type) throws Exception {
        var event = event(type);
        em.flush(); em.clear();
        var stats = statistics(); stats.clear();
        mvc.perform(get(url(event))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.metadata.phase").value("NOT_STARTED"))
                .andExpect(jsonPath("$.rounds.length()").value(0L));
        assertThat(stats.getEntityInsertCount() + stats.getEntityUpdateCount() + stats.getEntityDeleteCount()).isZero();
    }

    @Test
    void visibilityErrorsAndHeadDoNotLeakHiddenEvents() throws Exception {
        var event = event(TournamentType.KING_OF_COURT);
        event.setVisibilidad(TournamentVisibility.SOLO_POR_ENLACE);
        mvc.perform(head(url(event))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        for (String role : List.of("PLAYER", "OWNER", "CLUB_ADMIN", "SUPER_ADMIN")) {
            mvc.perform(get(url(event)).with(user("spectator").roles(role))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.metadata.eventId").value(event.getId()));
        }
        event.setEstado(TournamentStatus.BORRADOR); em.flush();
        mvc.perform(get(url(event))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LIVE_EVENT_NOT_FOUND"));
        event.setEstado(TournamentStatus.CANCELADO); event.setIsActive(false); em.flush();
        mvc.perform(get(url(event))).andExpect(status().isNotFound());
        for (String id : List.of("0", "-1", "abc", "9223372036854775808")) {
            mvc.perform(get("/api/public/events/"+id+"/live")).andExpect(status().isUnprocessableEntity())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("LIVE_INVALID_ID"));
        }
    }

    @Test
    void kingRankingScoresAndAliasAreAuthoritativeAndSafe() throws Exception {
        var event = event(TournamentType.KING_OF_COURT);
        var king = king(event, 2);
        var expected = kingService.getRanking(king.getId());
        var snapshot = (King) live.snapshot(event.getId());
        assertThat(snapshot.ranking()).extracting(KingRank::playerId).containsExactlyElementsOf(expected.stream().map(row -> row.getPlayerId()).toList());
        assertThat(snapshot.ranking().get(0).totalPoints()).isEqualTo(expected.get(0).getTotalPoints());
        assertThat(snapshot.ranking()).allSatisfy(row -> assertThat(row.playerNivel()).isNull());
        mvc.perform(get("/api/public/king-of-court/"+king.getId()+"/live")).andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.instanceId").value(king.getId()));
        mvc.perform(get("/preview/tournaments/king-of-court/"+king.getId()).with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
        var body = mvc.perform(get(url(event))).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("email", "phone", "password", "note", "actor", "secret@example.com");
        king.setIsActive(false); em.flush();
        var next = king(event, 1);
        mvc.perform(get("/api/public/king-of-court/"+king.getId()+"/live")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIVE_INSTANCE_CHANGED"))
                .andExpect(jsonPath("$.canonicalUrl").value("https://public.example.test/torneo/"+event.getId()+"/king-of-court"));
        assertThat(live.snapshot(event.getId()).metadata().instanceId()).isEqualTo(next.getId());
    }

    @Test
    void cancelledAndFinishedAreServerStatesAndPreviewKeepsGuard() throws Exception {
        var event = event(TournamentType.KING_OF_COURT);
        for (var status : List.of(TournamentStatus.CANCELADO, TournamentStatus.FINALIZADO)) {
            event.setEstado(status); em.flush();
            assertThat(live.snapshot(event.getId()).metadata().phase().name()).isEqualTo(status == TournamentStatus.CANCELADO ? "CANCELLED" : "FINISHED");
        }
        var preview = "/preview/torneo/"+event.getId()+"/king-of-court/history";
        mvc.perform(get(preview)).andExpect(status().isFound());
        mvc.perform(get(preview).with(user("admin").roles("SUPER_ADMIN"))).andExpect(status().isOk())
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
    }

    @Test
    void roundRobinDoesNotPretendToBePlayoff() throws Exception {
        var event = event(TournamentType.AMERICANO_TEAMS);
        rounds.save(AmericanoRound.builder().tournament(event).roundNumber(1).pointsPerMatch(32).isDoubles(true).courts(1).build());
        mvc.perform(get(url(event))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIVE_FORMAT_UNSUPPORTED"))
                .andExpect(jsonPath("$.canonicalUrl").value("https://public.example.test/tournaments/team-americano/"+event.getId()));
    }

    @Test
    void americanoPreservesRankingRoundsAndHiddenNotes() throws Exception {
        var event = event(TournamentType.AMERICANO);
        seedAmericano(event, 19);
        var expected = americano.getRankingWithDetails(event.getId(), RankingCriteria.builder().sortBy("score").ascending(false).build());
        var actual = (Americano) live.snapshot(event.getId());
        assertThat(actual.rounds()).hasSize(19);
        assertThat(actual.ranking()).extracting(AmericanoRank::playerId).containsExactlyElementsOf(expected.getRanking().stream().map(row -> row.getPlayerId()).toList());
        mvc.perform(get(url(event))).andExpect(status().isOk()).andExpect(jsonPath("$.metadata.topic").value(""))
                .andExpect(jsonPath("$.rounds[0].matches[0].note").doesNotExist());
    }

    @Test
    void drawnAmericanoDoesNotDeclareSecondSideWinner() {
        var event=event(TournamentType.AMERICANO); seedAmericano(event,1);
        var match=matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(event.getId()).get(0);
        match.complete(16,16); em.flush();em.clear();
        var snapshot=(Americano) live.snapshot(event.getId());
        assertThat(snapshot.rounds().get(0).matches().get(0).completed()).isTrue();
        assertThat(snapshot.rounds().get(0).matches().get(0).firstWinner()).isNull();
        match=matches.findById(match.getId()).orElseThrow();match.complete(21,11);em.flush();em.clear();
        assertThat(((Americano)live.snapshot(event.getId())).rounds().get(0).matches().get(0).firstWinner()).isTrue();
    }

    @Test
    void qualificationUsesRealServicesAndWhitelistsTeamContacts() throws Exception {
        var event = event(TournamentType.AMERICANO_TEAMS);
        seedPlayoff(event, 8);
        var expected = playoff.getQualRanking(event.getId()).getRanking();
        var actual = (Playoff) live.snapshot(event.getId());
        assertThat(actual.ranking()).extracting(TeamRank::id).containsExactlyElementsOf(expected.stream().map(row -> row.getId()).toList());
        var teams = expected;
        playoff.createQualificationMatch(event.getId(),teams.get(0).getId(),teams.get(1).getId(),1);
        actual = (Playoff) live.snapshot(event.getId());
        assertThat(actual.rounds()).flatExtracting(Round::matches).hasSize(1);
        var body = mvc.perform(get(url(event))).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("email", "phone", "password", "note", "secret@example.com");
    }

    @Test
    void queryCountDoesNotGrowPerKingRoundOrAmericanoMatch() {
        var smallKing = event(TournamentType.KING_OF_COURT); king(smallKing,1);
        var largeKing = event(TournamentType.KING_OF_COURT); king(largeKing,19);
        assertThat(queryCount(largeKing)).isLessThanOrEqualTo(queryCount(smallKing)+2);
        var small = event(TournamentType.AMERICANO); seedAmericano(small,1);
        var large = event(TournamentType.AMERICANO); seedAmericano(large,19);
        assertThat(queryCount(large)).isLessThanOrEqualTo(queryCount(small)+2);
    }

    @Test
    void playoffQueryCountDoesNotGrowPerTeamOrMatch() {
        var small=event(TournamentType.AMERICANO_TEAMS); seedPlayoff(small,8);
        var large=event(TournamentType.AMERICANO_TEAMS); seedPlayoff(large,16);
        assertThat(queryCount(large)).isLessThanOrEqualTo(queryCount(small)+2);
    }

    private void seedPlayoff(Tournament event, int count) {
        for(int i=0;i<count;i++) {
            var request=new TeamPlayoffAddTeamRequest(); request.setPlayer1Id(player().getId()); request.setPlayer2Id(player().getId());
            request.setRegistrationSource("ADMIN_MANUAL"); request.setHasPaid(true); request.setAttended(true);
            playoff.addTeam(event.getId(),request);
        }
        playoff.initQualification(event.getId(),2);
    }

    private long queryCount(Tournament event) {
        em.flush(); em.clear(); var statistics=statistics(); statistics.clear(); live.snapshot(event.getId());
        assertThat(statistics.getEntityInsertCount()+statistics.getEntityUpdateCount()+statistics.getEntityDeleteCount()).isZero();
        return statistics.getPrepareStatementCount();
    }
    private Statistics statistics() { return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics(); }
    private String url(Tournament event) { return "/api/public/events/"+event.getId()+"/live"; }
    private Tournament event(TournamentType type) {
        var club=clubs.save(Club.builder().nombre("Live test "+UUID.randomUUID()).direccion("Test").isActive(true).build());
        return events.save(Tournament.builder().clubId(club.getId()).nombre("Copa <&>").fechaInicio(LocalDate.now().plusDays(1))
                .horaInicio(LocalTime.NOON).generoFormato(GenderFormat.MIXTO).categoriaNivel(Nivel.C7).tipo(type)
                .modalidad(type==TournamentType.AMERICANO ? Modalidad.INDIVIDUAL : Modalidad.DOBLES).cupoMax(32).precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO).mostrarNivel(false).contactoOrganizador("secret@example.com").isActive(true).build());
    }
    private PlayerPadel player() {
        return players.save(PlayerPadel.builder().nombre("Длинное имя игрока").apellido(UUID.randomUUID().toString()).email("live-"+UUID.randomUUID()+"@example.com")
                .passwordHash("$2b$12$test").activo(true).emailConfirmado(true).oauth2User(false).nivelJugador(Nivel.C7).preferredLocale("es").build());
    }
    private TournamentKingOfCourt king(Tournament event, int count) {
        var king=new TournamentKingOfCourt(); king.setTournament(event); king.setIsActive(true); king.setCurrentRound(count); king.setMaxCourts(1); kings.save(king);
        var participants=List.of(player(),player(),player(),player());
        for(int i=0;i<4;i++) { var stat=new KingOfCourtPlayerStats(); stat.setTournamentKing(king); stat.setPlayer(participants.get(i)); stat.setTotalPoints(24-i); stat.setBonusPoints(2); kingStats.save(stat); }
        for(int i=1;i<=count;i++) {
            var round=new KingOfCourtRound(); round.setTournamentKing(king); round.setRoundNumber(i); kingRounds.save(round);
            var court=new KingOfCourtCourt(); court.setRound(round); court.setCourtNumber(1); court.setPlayers(participants); courts.save(court);
        }
        em.flush(); return king;
    }
    private void seedAmericano(Tournament event,int count) {
        var participants=List.of(player(),player(),player(),player());
        for(int i=0;i<4;i++) americanoPlayers.save(AmericanoPlayer.builder().tournament(event).player(participants.get(i)).initialPosition(i+1).currentPosition(i+1).totalScore(24-i).build());
        for(int i=1;i<=count;i++) {
            var round=rounds.save(AmericanoRound.builder().tournament(event).roundNumber(i).pointsPerMatch(32).isDoubles(false).courts(1).note("private-note").build());
            matches.save(AmericanoMatch.builder().tournament(event).round(round).matchNumber(1).courtNumber(1).isDoubles(false).team1Player1(participants.get(0)).team1Player2(participants.get(1))
                    .team2Player1(participants.get(2)).team2Player2(participants.get(3)).note("private-note").build());
        }
        em.flush(); em.clear();
    }
}
