package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.padelcoreservice.support.OwnerFixtures;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.persistence.clubs.ClubRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.util.concurrent.atomic.AtomicInteger;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.mail.username=test@example.com", "spring.mail.password=test-password",
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
    "recaptcha.site-key=test-site-key", "recaptcha.secret-key=test-secret-key"
})
@Transactional
class AdminCalendarIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired OwnerFixtures owners;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRepository tournaments;
    private static final AtomicInteger TEST_IP = new AtomicInteger();
    private String requestIp;
    Owner clubOwner;
    Owner other;
    Club venue;
    Club second;
    Club foreign;
    Tournament own;
    Tournament foreignEvent;

    @BeforeEach
    void prepare() {
        requestIp = "198.18.0." + TEST_IP.incrementAndGet();
        venue = venue("Calendar Norte"); second = venue("Calendar Sur"); foreign = venue("Private venue");
        clubOwner = account(OwnerRole.CLUB_ADMIN);
        clubOwner.setClubIds(new HashSet<>(Set.of(venue.getId(), second.getId())));
        owners.save(clubOwner);
        other = account(OwnerRole.ORGANIZER);
        own = event(clubOwner, venue, "2040-02-29", TournamentStatus.BORRADOR);
        foreignEvent = event(other, venue, "2040-02-29", TournamentStatus.PUBLICADO);
        event(other, foreign, "2040-02-29", TournamentStatus.PUBLICADO);
    }

    @Test
    void clubScopeAppliesToEventsCountsFiltersAndDirectDetail() throws Exception {
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02").with(user(clubOwner)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.periodCount").value(1))
            .andExpect(jsonPath("$.events.length()").value(1L)).andExpect(jsonPath("$.events[0].id").value(own.getId()))
            .andExpect(jsonPath("$.clubs[0].id").value(venue.getId()))
            .andExpect(content().string(not(containsString("Private venue"))))
            .andExpect(content().string(not(containsString("test-password"))));
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02")
                .param("clubId", foreign.getId().toString()).with(user(clubOwner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(0L))
            .andExpect(jsonPath("$.periodCount").value(1));
        mvc.perform(get("/admin/tournaments/" + foreignEvent.getId()).header("Accept", "application/json").with(user(clubOwner)))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = OwnerRole.class, names = {"SUPER_ADMIN", "ADMIN"})
    void globalRolesSeeAllOwners(OwnerRole role) throws Exception {
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02").with(user(account(role))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events[1].id").value(foreignEvent.getId()))
            .andExpect(content().string(containsString(foreign.getNombre())));
    }

    @ParameterizedTest
    @EnumSource(value = OwnerRole.class, names = {"ORGANIZER"})
    void organizersSeeOnlyTheirOwnEvents(OwnerRole role) throws Exception {
        Owner organizer = account(role);
        Tournament event = event(organizer, venue, "2040-02-29", TournamentStatus.PUBLICADO);
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02").with(user(organizer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(1L))
            .andExpect(jsonPath("$.events[0].id").value(event.getId()));
    }

    @Test
    void closedCancelledAndMovedEventsUseStoredState() throws Exception {
        own.setEstado(TournamentStatus.CERRADO); tournaments.save(own);
        Tournament cancelled = event(clubOwner, venue, "2040-02-10", TournamentStatus.CANCELADO);
        cancelled.setIsActive(false); tournaments.save(cancelled);
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02").with(user(clubOwner)))
            .andExpect(jsonPath("$.periodCount").value(2)).andExpect(jsonPath("$.events.length()").value(1L))
            .andExpect(jsonPath("$.events[0].status").value("CERRADO"));
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02").param("status", "CANCELADO").with(user(clubOwner)))
            .andExpect(jsonPath("$.events.length()").value(1L)).andExpect(jsonPath("$.events[0].id").value(cancelled.getId()));
        own.setFechaInicio(LocalDate.parse("2040-03-01")); tournaments.save(own);
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-03").with(user(clubOwner)))
            .andExpect(jsonPath("$.events[0].date").value("2040-03-01"));
    }

    @Test
    void allFiltersAndNoPageTruncation() throws Exception {
        own.setCategoriaNivel(Nivel.C7_C6); tournaments.save(own);
        for (int i = 0; i < 35; i++) { event(clubOwner, venue, "2040-02-29", TournamentStatus.PUBLICADO); }
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02").with(user(clubOwner)))
            .andExpect(jsonPath("$.events.length()").value(36L));
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02").param("level", "C6")
                .param("format", "AMERICANO_PAIRS").param("gender", "MASCULINO")
                .param("status", "BORRADOR").param("clubId", venue.getId().toString()).with(user(clubOwner)))
            .andExpect(jsonPath("$.events.length()").value(1L)).andExpect(jsonPath("$.events[0].id").value(own.getId()));
    }

    @Test
    void midnightYearBoundaryAndUnknownEndAreNotShiftedByDeviceZone() throws Exception {
        own.setFechaInicio(LocalDate.parse("2039-12-31")); own.setHoraInicio(LocalTime.of(23,30));
        own.setDurationMinutes(120); own.setTimeZone("America/Argentina/Buenos_Aires"); tournaments.save(own);
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-01").with(user(clubOwner)))
            .andExpect(jsonPath("$.events[0].date").value("2039-12-31"))
            .andExpect(jsonPath("$.events[0].endDate").value("2040-01-01"))
            .andExpect(jsonPath("$.events[0].endTime").value("01:30:00"))
            .andExpect(jsonPath("$.events[0].timeZone").value("America/Argentina/Buenos_Aires"));
        own.setDurationMinutes(30); tournaments.save(own);
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-01").with(user(clubOwner)))
            .andExpect(jsonPath("$.events.length()").value(0L));
        own.setDurationMinutes(null); own.setDuracion("неизвестно"); tournaments.save(own);
        mvc.perform(get("/api/admin/events/calendar").param("month", "2039-12").with(user(clubOwner)))
            .andExpect(jsonPath("$.events[0].endDate").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(TournamentType.class)
    void everyExistingFormatIsIncluded(TournamentType type) throws Exception {
        own.setTipo(type); tournaments.save(own);
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02").with(user(clubOwner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(1L))
            .andExpect(jsonPath("$.events[0].format").isNotEmpty());
    }

    @Test
    void emptyAccountInvalidMonthAndAnonymous() throws Exception {
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02").with(user(account(OwnerRole.CLUB_ADMIN))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(0L))
            .andExpect(jsonPath("$.periodCount").value(0)).andExpect(jsonPath("$.clubs.length()").value(0L));
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-13").with(user(clubOwner)))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/events/calendar")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/events/calendar").with(user("player").roles("PLAYER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void organizationComesFromOwnerAndFilterCannotExpandClubScope() throws Exception {
        clubOwner.setOrganizationName("Black Padel QA"); owners.save(clubOwner);
        other.setOrganizationName("E-Padel QA"); owners.save(other);
        own.setCreatedBy(other.getId()); tournaments.save(own);
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02").with(user(clubOwner)))
            .andExpect(jsonPath("$.events[0].organizer.id").value(clubOwner.getId()))
            .andExpect(jsonPath("$.events[0].organizer.name").value("Black Padel QA"))
            .andExpect(jsonPath("$.organizers.length()").value(1L))
            .andExpect(content().string(not(containsString("E-Padel QA"))));
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02")
                .param("organizerId", other.getId().toString()).with(user(clubOwner)))
            .andExpect(jsonPath("$.events.length()").value(0L))
            .andExpect(jsonPath("$.organizers.length()").value(1L));
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02")
                .param("organizerId", other.getId().toString()).param("clubId", venue.getId().toString())
                .param("format", "AMERICANO_PAIRS").param("gender", "MASCULINO").param("level", "C7")
                .param("status", "PUBLICADO").with(user(account(OwnerRole.SUPER_ADMIN))))
            .andExpect(jsonPath("$.events.length()").value(1L))
            .andExpect(jsonPath("$.events[0].id").value(foreignEvent.getId()));
    }

    @ParameterizedTest
    @EnumSource(OwnerRole.class)
    void everyManagingRoleCanSaveOnlyOrganizationAndReadFreshValue(OwnerRole role) throws Exception {
        Owner actor = account(role);
        mvc.perform(put("/api/admin/organizers/" + actor.getId() + "/organization").with(user(actor)).with(csrf())
                .contentType("application/json").content("{\"name\":\"  Organization QA  \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Organization QA"));
        mvc.perform(get("/api/admin/organizers/organizations").with(user(actor)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string(containsString("Organization QA")))
            .andExpect(content().string(not(containsString(actor.getEmail()))))
            .andExpect(content().string(not(containsString("test-password"))));
        Owner persisted = owners.findById(actor.getId()).orElseThrow();
        assertThat(persisted.getFirstName()).isEqualTo("Calendar");
        assertThat(persisted.getRole()).isEqualTo(role);
        mvc.perform(put("/api/admin/organizers/" + actor.getId() + "/organization").with(user(actor)).with(csrf())
                .contentType("application/json").content("{\"name\":\"   \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").isEmpty());
    }

    @Test
    void organizationSettingsProtectForeignAccountsCsrfAndLength() throws Exception {
        String endpoint = "/api/admin/organizers/" + other.getId() + "/organization";
        mvc.perform(get("/api/admin/organizers/organizations").with(user(clubOwner)))
            .andExpect(jsonPath("$.accounts.length()").value(1L))
            .andExpect(jsonPath("$.accounts[0].id").value(clubOwner.getId()));
        mvc.perform(put(endpoint).with(user(clubOwner)).with(csrf()).contentType("application/json")
                .content("{\"name\":\"forbidden\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(put(endpoint).with(user(other)).contentType("application/json")
                .content("{\"name\":\"missing csrf\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(put(endpoint).with(user(other)).with(csrf()).contentType("application/json")
                .content("{\"name\":\"" + "x".repeat(121) + "\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_ORGANIZATION_NAME"));
        assertThat(owners.findById(other.getId()).orElseThrow().getOrganizationName()).isNull();
        mvc.perform(put(endpoint).with(user(account(OwnerRole.SUPER_ADMIN))).with(csrf())
                .contentType("application/json").content("{\"name\":\"Managed QA\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Managed QA"));
        mvc.perform(get("/api/admin/organizers/organizations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/organizers/organizations").with(user("player").roles("PLAYER")))
            .andExpect(status().isForbidden());
        mvc.perform(put(endpoint).with(user("player").roles("PLAYER")).with(csrf())
                .contentType("application/json").content("{\"name\":\"forbidden\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void missingOrganizationIsNotReplacedWithClubOrPersonalName() throws Exception {
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02").with(user(clubOwner)))
            .andExpect(jsonPath("$.events[0].organizer.id").value(clubOwner.getId()))
            .andExpect(jsonPath("$.events[0].organizer.name").isEmpty());
        own.setOwnerId(null); tournaments.save(own);
        mvc.perform(get("/api/admin/events/calendar").param("month", "2040-02").with(user(account(OwnerRole.SUPER_ADMIN))))
            .andExpect(jsonPath("$.events[0].organizer").isEmpty());
    }

    private MockHttpServletRequestBuilder get(String path) {
        return MockMvcRequestBuilders.get(path).with(request -> {
            request.setRemoteAddr(requestIp);
            return request;
        });
    }

    private Club venue(String name) {
        return clubs.save(Club.builder().nombre(name + UUID.randomUUID()).isActive(true).build());
    }
    private Owner account(OwnerRole role) {
        return owners.save(Owner.builder().email(UUID.randomUUID() + "@example.com").password("test-password")
                .firstName("Calendar").lastName("QA").role(role).build());
    }
    private Tournament event(Owner owner, Club club, String date, TournamentStatus status) {
        return tournaments.save(Tournament.builder().ownerId(owner.getId()).clubId(club.getId()).nombre("Calendar QA")
            .fechaInicio(LocalDate.parse(date)).horaInicio(LocalTime.of(14,0)).durationMinutes(60)
            .timeZone("America/Argentina/Buenos_Aires").generoFormato(GenderFormat.MASCULINO).categoriaNivel(Nivel.C7)
            .tipo(TournamentType.AMERICANO).modalidad(Modalidad.DOBLES).cupoMax(16).precio(BigDecimal.ZERO)
            .contactoOrganizador("Calendar QA").estado(status).isActive(true).build());
    }
}
