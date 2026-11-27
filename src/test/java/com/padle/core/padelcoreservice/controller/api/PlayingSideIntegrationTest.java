package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.model.*;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.mapper.TournamentRegistrationMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import com.padle.core.padelcoreservice.service.*;
import com.padle.core.padelcoreservice.dto.GuestProfileClaimDtos.*;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.mapper.PublicLiveMapper;
import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.time.temporal.ChronoUnit;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Propagation;
import static com.padle.core.padelcoreservice.support.PlayerFixtures.playerAuthentication;
import static com.padle.core.padelcoreservice.support.PlayerFixtures.oauthPrincipal;
import static com.padle.core.padelcoreservice.support.PlayerFixtures.playerUser;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"recaptcha.enabled=false", "app.security.whitelisted-ips=127.0.0.1", "app.auth.google-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class PlayingSideIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PlayerRepository players;
    @Autowired PlayerService playerService;
    @Autowired GuestProfileClaimService claims;
    @Autowired PublicLiveMapper live;
    @Autowired EntityManager em;
    @MockitoBean EmailService email;
    @MockitoBean RecaptchaService captcha;
    private static final String URL = "/api/players/me/playing-side";

    private PlayerPadel player(boolean guest, PlayingSide side) {
        return players.saveAndFlush(PlayerPadel.builder().nombre("Fixture").apellido("Player")
                .email(UUID.randomUUID() + (guest ? "@1padel.guest" : "@example.com"))
                .telefono(guest ? "+54 9 11 2233-7781" : null).passwordHash("unused")
                .preferredLocale("es").nivelJugador(Nivel.SIN_ESPECIFICAR).playingSide(side).activo(true).emailConfirmado(true).build());
    }

    @Test void visitIsAccountWideAndLaterUsesTwentyFourHours() throws Exception {
        var p = player(false, null);
        mvc.perform(post(URL+"/visit").with(playerUser(p)).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.ask").value(true)).andExpect(jsonPath("$.levelMissing").value(true))
                .andExpect(jsonPath("$.playingSide").doesNotExist());
        em.flush(); em.clear();
        mvc.perform(post(URL+"/visit").with(playerUser(p)).with(csrf())).andExpect(jsonPath("$.ask").value(false));
        mvc.perform(post(URL+"/defer").with(playerUser(p)).with(csrf())).andExpect(status().isNoContent());
        assertThat(players.findById(p.getId()).orElseThrow().getPlayingSidePromptAfter())
                .isAfter(Instant.now().plus(Duration.ofHours(23)));
        players.findById(p.getId()).orElseThrow().setPlayingSidePromptAfter(Instant.now().minusSeconds(1));
        em.flush(); em.clear();
        mvc.perform(post(URL+"/visit").with(playerUser(p)).with(csrf())).andExpect(jsonPath("$.ask").value(true));
    }

    @Test void answerPersistsAndChangingLevelDoesNotClearIt() throws Exception {
        var p = player(false, null);
        mvc.perform(patch(URL).with(playerUser(p)).with(csrf()).contentType("application/json")
                        .content("{\"playingSide\":\"LEFT\",\"nivelJugador\":\"C7\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.playingSide").value("LEFT"))
                .andExpect(jsonPath("$.nivelJugador").value("C7"));
        em.flush(); em.clear();
        mvc.perform(patch("/api/players/me/level").with(playerUser(p)).with(csrf()).contentType("application/json")
                .content("{\"nivelJugador\":\"D7\"}")).andExpect(status().isNoContent());
        em.clear();
        mvc.perform(get("/api/players/me").with(playerUser(p))).andExpect(jsonPath("$.playingSide").value("LEFT"));
        mvc.perform(patch(URL).with(playerUser(p)).with(csrf()).contentType("application/json")
                .content("{\"playingSide\":\"EITHER\"}")).andExpect(jsonPath("$.nivelJugador").value("D7"));
        mvc.perform(post(URL+"/visit").with(playerUser(p)).with(csrf())).andExpect(jsonPath("$.ask").value(false));
        verifyNoInteractions(email);
    }

    @Test void invalidAnswerAndCsrfCannotModifyOwnOrAnotherPlayer() throws Exception {
        var p = player(false, null); var other = player(false, PlayingSide.RIGHT);
        mvc.perform(patch(URL).with(playerUser(p)).contentType("application/json").content("{\"playingSide\":\"LEFT\"}"))
                .andExpect(status().isForbidden());
        for (String input : new String[]{"{}", "{\"playingSide\":\"BOGUS\"}", "{\"playingSide\":\"LEFT\",\"nivelJugador\":\"TODOS\"}"}) {
            mvc.perform(patch(URL).with(playerUser(p)).with(csrf()).contentType("application/json").content(input))
                    .andExpect(status().isUnprocessableEntity());
        }
        mvc.perform(patch(URL).with(playerUser(p)).with(csrf()).contentType("application/json")
                .content("{\"playingSide\":\"LEFT\",\"playerId\":"+other.getId()+"}"))
                .andExpect(status().isOk());
        assertThat(other.getPlayingSide()).isEqualTo(PlayingSide.RIGHT);
    }

    @Test void ownerCannotAnswerAsPlayerAndClubCannotEditForeignProfile() throws Exception {
        var p = player(false, null);
        var owner = Owner.builder().id(987L).email("club@example.com").password("unused").role(OwnerRole.CLUB_ADMIN).build();
        mvc.perform(patch(URL).with(user(owner)).with(csrf()).contentType("application/json")
                .content("{\"playingSide\":\"LEFT\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/players/"+p.getId()+"/playing-side").with(user(owner)).with(csrf()).param("playingSide","LEFT"))
                .andExpect(status().is3xxRedirection());
        assertThat(p.getPlayingSide()).isNull();
    }

    @Test void googlePrincipalCanAnswerWithoutChangingLevel() throws Exception {
        var p = player(false, null);
        var oauth = oauthPrincipal(mock(OAuth2User.class), p);
        var auth = UsernamePasswordAuthenticationToken.authenticated(oauth,"",oauth.getAuthorities());
        mvc.perform(patch(URL).with(authentication(auth)).with(csrf()).contentType("application/json")
                .content("{\"playingSide\":\"RIGHT\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.nivelJugador").value("SIN_ESPECIFICAR"));
    }

    @Test void detachedProfileSavePreservesSideAndDeferTimestamp() {
        var p = player(false, null); em.detach(p);
        var current = players.findById(p.getId()).orElseThrow(); current.setPlayingSide(PlayingSide.LEFT);
        var after = Instant.now().plusSeconds(86000).truncatedTo(ChronoUnit.MICROS); current.setPlayingSidePromptAfter(after); em.flush(); em.clear();
        p.setNombre("Changed"); playerService.actualizarJugador(p); em.flush(); em.clear();
        var stored = players.findById(p.getId()).orElseThrow();
        assertThat(stored.getPlayingSide()).isEqualTo(PlayingSide.LEFT);
        assertThat(stored.getPlayingSidePromptAfter()).isEqualTo(after);
    }

    @ParameterizedTest
    @CsvSource(value={"LEFT,null,LEFT", "null,RIGHT,RIGHT", "EITHER,EITHER,EITHER", "null,null,null"}, nullValues="null")
    void mergeKeepsKnownSide(PlayingSide accountSide, PlayingSide guestSide, PlayingSide expected) {
        var account = player(false, accountSide); var guest = player(true, guestSide);
        claims.manualMerge(new ManualMerge(guest.getId(),account.getId(),true,Verification.IN_PERSON,null,false),987L);
        assertThat(account.getPlayingSide()).isEqualTo(expected);
    }

    @Test void mergeConflictIsVisibleAndRequiresExplicitChoice() {
        var account = player(false, PlayingSide.LEFT); var guest = player(true, PlayingSide.RIGHT);
        assertThat(claims.manualReview(guest.getId(),account.getId()).differences()).contains("PLAYING_SIDE_DIFFERS");
        assertThatThrownBy(() -> claims.manualMerge(new ManualMerge(guest.getId(),account.getId(),true,Verification.IN_PERSON,null,false),987L))
                .isInstanceOf(AccountException.class).hasMessage("GUEST_CLAIM_PLAYING_SIDE_CONFLICT");
        assertThat(guest.isActivo()).isTrue(); assertThat(account.getPlayingSide()).isEqualTo(PlayingSide.LEFT);
    }

    @ParameterizedTest @CsvSource({"ACCOUNT,LEFT", "GUEST,RIGHT"})
    void mergeResolvesConflictExplicitly(SideChoice choice, PlayingSide expected) {
        var account = player(false, PlayingSide.LEFT); var guest = player(true, PlayingSide.RIGHT);
        claims.manualMerge(new ManualMerge(guest.getId(),account.getId(),true,Verification.IN_PERSON,null,false,choice),987L);
        assertThat(account.getPlayingSide()).isEqualTo(expected);
        assertThat(guest.isActivo()).isFalse();
    }

    @Test void publicLiveMasksBothSidesWhenLevelDisplayIsDisabled() {
        var team = new AmericanoTeamDto(); team.setPlayer1PlayingSide(PlayingSide.LEFT); team.setPlayer2PlayingSide(PlayingSide.RIGHT);
        assertThat(live.teamRank(team,false).player1().playingSide()).isNull();
        assertThat(live.teamRank(team,false).player2().playingSide()).isNull();
        assertThat(live.teamRank(team,true).player1().playingSide()).isEqualTo(PlayingSide.LEFT);
        assertThat(live.teamRank(team,true).player2().playingSide()).isEqualTo(PlayingSide.RIGHT);
    }

    @ParameterizedTest @CsvSource(value={"null,null", "RIGHT,RIGHT"}, nullValues="null")
    void signupStoresOptionalAnswer(PlayingSide input, PlayingSide expected) throws Exception {
        when(captcha.verify(null)).thenReturn(true);
        String emailAddress = UUID.randomUUID()+"@gmail.com";
        var request = post("/players/registro").with(csrf()).param("nombre","Maria").param("apellido","Garcia")
                .param("email",emailAddress).param("nivelJugador","C7").param("telefono","+12025550189")
                .param("password","Fixture-Password1!").param("confirmPassword","Fixture-Password1!");
        if (input != null) request.param("playingSide",input.name());
        mvc.perform(request).andExpect(status().is3xxRedirection());
        assertThat(players.findByEmail(emailAddress).orElseThrow().getPlayingSide()).isEqualTo(expected);
    }

    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void simultaneousVisitsOnlyShowOnePrompt() throws Exception {
        var p = player(false,null);
        var auth = playerAuthentication(p);
        var service = context.getBean(PlayingSideService.class);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); return service.visit(auth).ask(); });
            var second = pool.submit(() -> { start.await(); return service.visit(auth).ask(); });
            start.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),
                    second.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        } finally { pool.shutdownNow(); players.deleteById(p.getId()); }
    }
    @Autowired ApplicationContext context;

    @Autowired ClubRepository clubs;
    @Autowired TournamentRepository tournaments;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired TournamentRegistrationMapper registrationMapper;

    private Tournament event() {
        var club = clubs.save(Club.builder().nombre("Fixture "+UUID.randomUUID()).isActive(true).build());
        return tournaments.save(Tournament.builder().clubId(club.getId()).ownerId(987L).nombre("Fixture")
                .fechaInicio(LocalDate.now().plusDays(1)).horaInicio(LocalTime.NOON).generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.TODOS).tipo(TournamentType.AMERICANO_TEAMS).modalidad(Modalidad.DOBLES)
                .cupoMax(8).precio(BigDecimal.ZERO).estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("fixture@example.test").mostrarNivel(false).isActive(true).build());
    }

    @Test void rosterRequiresOwnershipAndOmitsContactData() throws Exception {
        var event = event(); var p = player(false, PlayingSide.LEFT);
        registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(p)
                .status(RegistrationStatus.SOLO_SEARCH).isActive(true).build());
        var own = Owner.builder().id(987L).email("club@example.test").role(OwnerRole.CLUB_ADMIN).build();
        String url = "/api/admin/events/"+event.getId()+"/participant-preferences";
        mvc.perform(get(url).with(user(own))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].playingSide").value("LEFT"))
                .andExpect(jsonPath("$[0].email").doesNotExist()).andExpect(jsonPath("$[0].telefono").doesNotExist());
        var other = Owner.builder().id(988L).email("other@example.test").role(OwnerRole.CLUB_ADMIN).build();
        mvc.perform(get(url).with(user(other))).andExpect(status().isForbidden());
        mvc.perform(get(url).with(playerUser(p))).andExpect(status().isForbidden());
        var root = Owner.builder().id(999L).email("root@example.test").role(OwnerRole.SUPER_ADMIN).build();
        mvc.perform(get(url).with(user(root))).andExpect(status().isOk());
    }

    @Test void publicPartnerSearchRespectsEventVisibility() {
        var event = event(); var p = player(false, PlayingSide.RIGHT);p.setNivelJugador(Nivel.C7);
        var row = TournamentRegistration.builder().tournament(event).player(p).shareContacts(false).build();
        assertThat(registrationMapper.toLookingDto(row).getPlayingSide()).isNull();
        assertThat(registrationMapper.toLookingDto(row).getNivel()).isNull();
        event.setMostrarNivel(true);
        var visible = registrationMapper.toLookingDto(row);
        assertThat(visible.getPlayingSide()).isEqualTo(PlayingSide.RIGHT);
        assertThat(visible.getNivel()).isEqualTo("C7");
        assertThat(visible.getTelefono()).isNull();
    }
}
