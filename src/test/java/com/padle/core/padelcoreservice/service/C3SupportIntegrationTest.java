package com.padle.core.padelcoreservice.service;

import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.support.OwnerFixtures;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import com.padle.core.padelcoreservice.exception.PairRegistrationException;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.mapper.TournamentMapper;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.persistence.clubs.ClubRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;
import static com.padle.core.padelcoreservice.support.PlayerFixtures.playerUser;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.analytics.meta.enabled=false", "app.analytics.meta.access-token=",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false", "spring.mail.username=", "spring.mail.password=",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test"
})
@AutoConfigureMockMvc
@Transactional
class C3SupportIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PlayerRepository players;
    @Autowired OwnerFixtures owners;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRepository events;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired TournamentService service;
    @Autowired DoubleTournamentRegistrationService doubles;
    @Autowired TournamentMapper mapper;
    @Autowired EntityManager entities;
    @MockitoBean EmailService email;

    private Owner owner(OwnerRole role, Club club) {
        return owners.saveAndFlush(Owner.builder().email(UUID.randomUUID()+"@example.test").password("test")
                .firstName("Учебный").lastName("Организатор").role(role).isActive(true)
                .clubId(club.getId()).clubIds(Set.of(club.getId())).build());
    }

    private Club club() {
        return clubs.saveAndFlush(Club.builder().nombre("C3 "+UUID.randomUUID()).isActive(true).build());
    }

    private TournamentDto event(Club club, String level) {
        return TournamentDto.builder().clubId(club.getId()).fechaInicio(LocalDate.now().plusDays(20))
                .horaInicio(LocalTime.NOON).durationMinutes(90).timeZone("America/Argentina/Buenos_Aires")
                .generoFormato(GenderFormat.MASCULINO).categoriaNivel(level).tipo(TournamentType.AMERICANO)
                .modalidad(Modalidad.INDIVIDUAL).cupoMax(1).precio(BigDecimal.ZERO).moneda("ARS")
                .estado(TournamentStatus.REGISTRO_ABIERTO).contactoOrganizador("+5491123456789").build();
    }

    private PlayerPadel player(Nivel level) {
        String phone = "+54911"+java.util.concurrent.ThreadLocalRandom.current().nextInt(10000000,99999999);
        return players.saveAndFlush(PlayerPadel.builder().nombre("Учебный").apellido("C3")
                .email(UUID.randomUUID()+"@example.test").telefono(phone).passwordHash("test")
                .activo(true).emailConfirmado(true).nivelJugador(level).preferredLocale("ru").build());
    }

    @ParameterizedTest
    @ValueSource(strings = {"C3", "С3", "с3"})
    void profileJsonIsCanonicalAfterSaveAndFreshAuthentication(String input) throws Exception {
        var player = player(Nivel.C7);
        mvc.perform(patch("/api/players/me/level").with(playerUser(player)).with(csrf())
                        .contentType("application/json").content("{\"nivelJugador\":\""+input+"\"}"))
                .andExpect(status().isNoContent());
        entities.clear();
        var fresh = players.findById(player.getId()).orElseThrow();
        assertThat(fresh.getNivelJugador()).isEqualTo(Nivel.C3);
        mvc.perform(get("/api/players/me").with(playerUser(fresh)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nivelJugador").value("C3"));
    }

    @ParameterizedTest
    @EnumSource(value=OwnerRole.class,names={"SUPER_ADMIN","ADMIN","ORGANIZER","CLUB_ADMIN"})
    void rolesCreateEditAndFilterTheirEventWithoutExpandingForeignAccess(OwnerRole role) throws Exception {
        var venue = club();
        var actor = owner(role, venue);
        var created = service.createTournament(event(venue, "С3"), actor.getId());
        assertThat(created.getCategoriaNivel()).isEqualTo("C3");
        created.setCategoriaNivel("С3/С4");
        assertThat(service.updateTournament(created.getId(), created, actor)).get()
                .extracting(TournamentDto::getCategoriaNivel).isEqualTo("C4_C3");
        String month = created.getFechaInicio().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        mvc.perform(get("/api/admin/events/calendar").with(user(actor)).param("month", month).param("level", "С3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events[0].id").value(created.getId()));
        mvc.perform(get("/admin/tournaments").with(user(actor)).param("level", "С3"))
                .andExpect(status().isOk()).andExpect(model().attribute("selectedLevel", Nivel.C3));
        if (role == OwnerRole.ORGANIZER || role == OwnerRole.CLUB_ADMIN) {
            var stranger = owner(role, venue);
            mvc.perform(get("/admin/tournaments/"+created.getId()).with(user(stranger)).accept("application/json"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void neighbouringPlayerCanEnterButCapacityAndFarLevelStillApply() {
        var venue = club();
        var actor = owner(OwnerRole.SUPER_ADMIN, venue);
        var created = service.createTournament(event(venue, "C3"), actor.getId());
        var neighbour = player(Nivel.C4);
        var same = player(Nivel.C3);
        var distant = player(Nivel.C5);
        service.registerPlayer(created.getId(), neighbour.getId());
        service.registerPlayer(created.getId(), same.getId());
        assertThat(registrations.findByTournamentIdAndPlayerId(created.getId(), neighbour.getId())).get()
                .extracting(TournamentRegistration::getStatus).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.findByTournamentIdAndPlayerId(created.getId(), same.getId())).get()
                .extracting(TournamentRegistration::getStatus).isEqualTo(RegistrationStatus.WAITLIST);
        assertThatThrownBy(() -> service.registerPlayer(created.getId(), distant.getId()))
                .isInstanceOf(TournamentRegistrationException.class);
        assertThat(registrations.findByTournamentId(created.getId())).hasSize(2);
        assertThat(mapper.toDto(events.findById(created.getId()).orElseThrow()).getCategoriaNivel()).isEqualTo("C3");
    }

    @Test
    void pairAdmissionAndFullCapacityKeepBothPlayersInOneQueuePlace() {
        var venue = club();
        var actor = owner(OwnerRole.SUPER_ADMIN, venue);
        var draft = event(venue, "C3");
        draft.setModalidad(Modalidad.DOBLES);
        draft.setTipo(TournamentType.AMERICANO_TEAMS);
        var created = service.createTournament(draft, actor.getId());
        var first = player(Nivel.C3);
        var partner = player(Nivel.C4);
        doubles.registerForDoubleTournament(created, first.getId(), PartnerRegistrationDto.builder()
                .existingUserId(partner.getId()).build());
        var reserve = player(Nivel.C3);
        var reservePartner = player(Nivel.C4);
        doubles.registerForDoubleTournament(created, reserve.getId(), PartnerRegistrationDto.builder()
                .existingUserId(reservePartner.getId()).build());
        assertThat(registrations.findByTournamentId(created.getId())).hasSize(4);
        assertThat(registrations.countConfirmedPairs(created.getId())).isEqualTo(1);
        assertThat(registrations.findByTournamentIdAndPlayerId(created.getId(), reserve.getId())).get()
                .extracting(TournamentRegistration::getStatus).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(registrations.findByTournamentIdAndPlayerId(created.getId(), reservePartner.getId())).get()
                .extracting(TournamentRegistration::getStatus).isEqualTo(RegistrationStatus.WAITLIST);
        var distant = player(Nivel.C5);
        assertThatThrownBy(() -> doubles.registerForDoubleTournament(created, distant.getId(),
                PartnerRegistrationDto.builder().existingUserId(player(Nivel.C3).getId()).build()))
                .isInstanceOf(PairRegistrationException.class);
        assertThat(registrations.findByTournamentId(created.getId())).hasSize(4);
    }
}
