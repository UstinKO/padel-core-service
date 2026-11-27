package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.support.OwnerFixtures;
import com.padle.core.padelcoreservice.dto.ClubDto;
import com.padle.core.padelcoreservice.dto.OwnerDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.padelcoreservice.service.ClubAccountService;
import com.padle.core.padelcoreservice.service.ClubService;
import com.padle.core.padelcoreservice.service.OwnerService;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.TournamentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com", "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key", "recaptcha.secret-key=test-secret-key",
        "app.scheduling.enabled=false"
})
@Transactional
class ClubOwnershipIntegrationTest {
    @Autowired ClubAccountService accounts;
    @Autowired ClubService clubService;
    @Autowired OwnerService profiles;
    @Autowired TournamentService tournaments;
    @Autowired TournamentAccessService access;
    @Autowired OwnerFixtures owners;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRepository events;
    @Autowired PlayerRepository players;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired AdminController admin;
    @Autowired AdminPlayerController playerAdmin;
    @Autowired MockMvc mvc;

    private Club first;
    private Club second;
    private Club third;
    private Owner actor;
    private Owner other;
    private Owner root;

    @BeforeEach
    void prepare() {
        first = club(); second = club(); third = club();
        actor = owner(OwnerRole.CLUB_ADMIN);
        other = owner(OwnerRole.CLUB_ADMIN);
        root = owner(OwnerRole.SUPER_ADMIN);
        accounts.assign(root, actor.getId(), Set.of(first.getId(), second.getId()));
        accounts.assign(root, other.getId(), Set.of(first.getId()));
    }

    @Test
    void testTournamentFlagRequiresSuperAdminAndCanBeReversed() throws Exception {
        TournamentDto own = tournaments.createTournament(form(first), actor.getId());
        mvc.perform(post("/admin/tournaments/" + own.getId() + "/test-status")
                .with(user(actor)).with(csrf()).param("testTournament", "true").header("Accept", "application/json"))
                .andExpect(status().isForbidden());
        assertThat(events.findById(own.getId()).orElseThrow().getTestTournament()).isFalse();
        mvc.perform(post("/admin/tournaments/" + own.getId() + "/test-status")
                .with(user(root)).with(csrf()).param("testTournament", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(events.findById(own.getId()).orElseThrow().getTestTournament()).isTrue();
        mvc.perform(post("/admin/tournaments/" + own.getId() + "/test-status")
                .with(user(root)).with(csrf()).param("testTournament", "false"))
                .andExpect(status().is3xxRedirection());
        assertThat(events.findById(own.getId()).orElseThrow().getTestTournament()).isFalse();
    }

    @Test
    void creationUsesSelectedAssignedClubAndRejectsUnassignedOrInactive() {
        TournamentDto one = tournaments.createTournament(form(first), actor.getId());
        TournamentDto two = tournaments.createTournament(form(second), actor.getId());
        assertThat(one.getClubId()).isEqualTo(first.getId());
        assertThat(two.getClubId()).isEqualTo(second.getId());
        assertThat(two.getOwnerId()).isEqualTo(actor.getId());
        long before = events.count();
        assertThatThrownBy(() -> tournaments.createTournament(form(third), actor.getId()))
                .isInstanceOf(AccessDeniedException.class);
        second.setIsActive(false);
        clubs.saveAndFlush(second);
        assertThatThrownBy(() -> tournaments.createTournament(form(second), actor.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(events.count()).isEqualTo(before);
    }

    @Test
    void sameClubDoesNotGiveAccessToAnotherCreatorsEvent() throws Exception {
        TournamentDto own = tournaments.createTournament(form(first), actor.getId());
        TournamentDto foreign = tournaments.createTournament(form(first), other.getId());
        for (String suffix : List.of("", "/edit", "/copy", "/payments")) {
            mvc.perform(get("/admin/tournaments/" + foreign.getId() + suffix).with(user(actor)).header("Accept", "application/json"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/tournaments/" + foreign.getId() + "/payments/save")
                .with(user(actor)).with(csrf()).header("Accept", "application/json")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/tournaments/" + own.getId()).with(user(actor)))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/tournaments/" + own.getId() + "/edit").with(user(actor)))
                .andExpect(status().isOk()).andExpect(model().attribute("isClubAdmin", true));
        mvc.perform(get("/admin/tournaments/" + foreign.getId()).with(user(root)))
                .andExpect(status().isOk());
        mvc.perform(get("/admin/tournaments/" + foreign.getId() + "/payments").with(user(root)))
                .andExpect(status().isOk());
    }

    @Test
    void assignmentRequiresSuperAdminAndCsrfAndRevocationAppliesToOpenSession() throws Exception {
        mvc.perform(post("/admin/club-accounts/" + actor.getId() + "/clubs")
                .param("clubIds", third.getId().toString()).with(user(actor)).with(csrf()).header("Accept", "application/json"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/club-accounts/" + actor.getId() + "/clubs")
                .param("clubIds", third.getId().toString()).with(user(root)).header("Accept", "application/json"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/club-accounts/" + actor.getId() + "/clubs")
                .param("clubIds", third.getId().toString()).with(user(root)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(accounts.assignedClubIds(actor)).containsExactly(third.getId());
        assertThatThrownBy(() -> accounts.assertCanCreateAt(actor, first.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> accounts.assign(actor, actor.getId(), Set.of(first.getId())))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> accounts.assign(root, actor.getId(), Set.of(Long.MAX_VALUE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(accounts.assignedClubIds(actor)).containsExactly(third.getId());
        accounts.assign(root, actor.getId(), Set.of());
        assertThat(accounts.assignedClubIds(actor)).isEmpty();
    }

    @Test
    void apiPreservesOrganizerCreationAndClubMetadataOnUpdate() throws Exception {
        Owner organizer = owner(OwnerRole.ORGANIZER);
        String name = "Club API " + UUID.randomUUID();
        String body = """
                {"nombre":"%s","direccion":"Calle 1","zonaCiudad":"Madrid","telefonoContacto":"555",
                 "id":-1,"createdBy":-1,"isActive":false}
                """.formatted(name);

        mvc.perform(post("/api/clubs").with(user(actor)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/clubs").with(user(organizer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdBy").value(organizer.getId()))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.direccionCompleta").value("Calle 1, Madrid"));
        var created = clubs.findByNombre(name).orElseThrow();
        assertThat(created.getId()).isPositive();
        assertThat(created.getCreatedAt()).isNotNull();

        mvc.perform(post("/api/clubs").with(user(organizer)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/clubs/search").with(user(root)).param("q", name))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(created.getId()));
        var update = """
                {"nombre":"%s","zonaCiudad":"Córdoba","id":-1,"createdBy":-1,"isActive":false}
                """.formatted(name);
        mvc.perform(put("/api/clubs/" + created.getId()).with(user(root)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(created.getId()))
                .andExpect(jsonPath("$.createdBy").value(organizer.getId()))
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.direccionCompleta").value("Córdoba"));
        clubs.flush();
        assertThat(clubs.findById(created.getId()).orElseThrow().getTelefonoContacto()).isNull();
    }

    @Test
    void statusRedirectAndTournamentGuardKeepTheirRules() throws Exception {
        var base = "/admin/clubs/" + first.getId();
        mvc.perform(post(base + "/toggle-status").with(user(actor)).with(csrf()))
                .andExpect(redirectedUrl("/admin/clubs")).andExpect(flash().attributeExists("errorMessage"));
        assertThat(clubs.findById(first.getId()).orElseThrow().getIsActive()).isTrue();
        mvc.perform(post(base + "/toggle-status").with(user(root)).with(csrf()))
                .andExpect(redirectedUrl("/admin/clubs")).andExpect(flash().attributeExists("successMessage"));
        assertThat(clubs.findById(first.getId()).orElseThrow().getIsActive()).isFalse();

        var event = tournaments.createTournament(form(second), actor.getId());
        mvc.perform(delete("/api/clubs/" + second.getId()).with(user(root)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(clubs.findById(second.getId()).orElseThrow().getIsActive()).isFalse();
        assertThat(events.findById(event.getId())).isPresent();
        mvc.perform(delete("/api/clubs/" + third.getId() + "/hard").with(user(root)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(clubs.findById(third.getId())).isEmpty();
        assertThatThrownBy(() -> clubService.hardDeleteClub(second.getId(), root))
                .isInstanceOf(InvalidStateException.class);
        assertThat(clubs.findById(second.getId())).isPresent();
    }

    @Test
    void clubCreationProfileAndAssignmentJoinTheCallerRollback() {
        var created = clubService.createClub(ClubDto.builder().nombre("Rollback " + UUID.randomUUID()).build(),
                root.getId());
        profiles.updateOwner(actor.getId(), OwnerDto.builder().firstName("Updated").lastName("Club")
                .phone("+5491100001234").isActive(true).build(), root);
        accounts.assign(root, actor.getId(), Set.of(created.getId()));
        owners.flush();
        assertThat(profiles.getOwnerById(actor.getId(), root).getFirstName()).isEqualTo("Updated");
        assertThat(owners.findById(actor.getId()).orElseThrow().getClubId()).isEqualTo(created.getId());
        assertThat(accounts.assignedClubIds(actor)).containsExactly(created.getId());

        TestTransaction.flagForRollback();
        TestTransaction.end();
        assertThat(clubs.findById(created.getId())).isEmpty();
        assertThat(owners.findById(actor.getId())).isEmpty();
    }

    @Test
    void ownerCanMoveBetweenAssignedClubsWithoutChangingAuthorship() {
        TournamentDto own = tournaments.createTournament(form(first), actor.getId());
        own.setClubId(second.getId());
        assertThat(tournaments.updateTournament(own.getId(), own, actor).orElseThrow().getClubId())
                .isEqualTo(second.getId());
        own.setClubId(third.getId());
        assertThatThrownBy(() -> tournaments.updateTournament(own.getId(), own, actor))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(events.findById(own.getId()).orElseThrow().getOwnerId()).isEqualTo(actor.getId());
        accounts.assign(root, actor.getId(), Set.of());
        assertThatCode(() -> access.assertCanManageTournament(actor, own.getId())).doesNotThrowAnyException();
        assertThatCode(() -> access.assertCanManageTournament(root, own.getId())).doesNotThrowAnyException();
    }

    @Test
    @SuppressWarnings("unchecked")
    void panelAndPlayerHistoryOnlyIncludeOwnEventsAcrossAssignedClubs() {
        TournamentDto own1 = tournaments.createTournament(form(first), actor.getId());
        TournamentDto own2 = tournaments.createTournament(form(second), actor.getId());
        TournamentDto foreign = tournaments.createTournament(form(first), other.getId());
        PlayerPadel shared = player();
        PlayerPadel foreignOnly = player();
        register(own1, shared); register(foreign, shared); register(foreign, foreignOnly);
        var model = new ExtendedModelMap();
        admin.adminPanel(model, actor);
        assertThat(model.get("totalTournaments")).isEqualTo(2L);
        assertThat(model.get("totalWaitlist")).isEqualTo(1L);
        assertThat(model.get("totalPlayers")).isEqualTo(1L);
        assertThat((List<TournamentDto>) model.get("recentTournaments"))
                .extracting(TournamentDto::getId).containsExactlyInAnyOrder(own1.getId(), own2.getId());
        var playerModel = new ExtendedModelMap();
        playerAdmin.viewPlayer(shared.getId(), playerModel, actor);
        assertThat((List<TournamentRegistrationDto>) playerModel.get("registrations"))
                .extracting(TournamentRegistrationDto::getTournamentId).containsExactly(own1.getId());
        assertThatThrownBy(() -> playerAdmin.viewPlayer(foreignOnly.getId(), new ExtendedModelMap(), actor))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void singleLegacyAssignmentIsStillAvailable() {
        Owner legacy = owner(OwnerRole.CLUB_ADMIN);
        legacy.setClubId(first.getId());
        owners.saveAndFlush(legacy);
        assertThat(accounts.assignedClubIds(legacy)).containsExactly(first.getId());
        assertThatCode(() -> accounts.assertCanCreateAt(legacy, first.getId())).doesNotThrowAnyException();
    }

    private Owner owner(OwnerRole role) {
        return owners.save(Owner.builder().email(UUID.randomUUID() + "@example.com")
                .password("irrelevant-hash").firstName("Test").lastName("Club").role(role).build());
    }

    private Club club() {
        return clubs.save(Club.builder().nombre("Test " + UUID.randomUUID()).isActive(true).build());
    }

    private TournamentDto form(Club club) {
        return TournamentDto.builder().clubId(club.getId()).fechaInicio(LocalDate.now().plusDays(7))
                .horaInicio(LocalTime.NOON).generoFormato(GenderFormat.MASCULINO).categoriaNivel("C7")
                .tipo(TournamentType.AMERICANO).modalidad(Modalidad.INDIVIDUAL).cupoMax(8)
                .precio(BigDecimal.TEN).moneda("ARS").contactoOrganizador("+5491123456789").build();
    }

    private PlayerPadel player() {
        return players.save(PlayerPadel.builder().nombre("Test").apellido("Player")
                .email(UUID.randomUUID() + "@example.com").passwordHash("irrelevant-hash")
                .nivelJugador(Nivel.C7).preferredLocale("es").oauth2User(false).activo(true).emailConfirmado(true).build());
    }

    private void register(TournamentDto event, PlayerPadel player) {
        registrations.save(TournamentRegistration.builder().tournament(events.findById(event.getId()).orElseThrow())
                .player(player).status(RegistrationStatus.WAITLIST).isActive(true).build());
    }
}
