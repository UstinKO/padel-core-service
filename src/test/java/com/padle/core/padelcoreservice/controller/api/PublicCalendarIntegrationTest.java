package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.persistence.clubs.ClubRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class PublicCalendarIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ClubRepository clubs;
    @Autowired private OwnerRepository owners;
    @Autowired private TournamentRepository events;
    @Autowired private PlayerRepository players;
    @Autowired private TournamentRegistrationRepository registrations;
    private Club venue;
    private Owner organizer;
    private final LocalDate day = LocalDate.of(2026, 11, 15);

    @BeforeEach
    void prepare() {
        venue = clubs.save(Club.builder().nombre("Public QA " + UUID.randomUUID()).isActive(true).build());
        organizer = owners.save(Owner.builder().email(UUID.randomUUID() + "@example.test").password("unused")
                .firstName("Private account name").lastName("Private surname").organizationName("Public organization")
                .role(OwnerRole.ORGANIZER).isActive(true).build());
    }

    @Test
    void anonymousMonthUsesPublicScopeAndWholeVisibleRangeWithoutPageLimit() throws Exception {
        for (int i = 0; i < 65; i++) event(day, LocalTime.of(8, 0).plusMinutes(i), Nivel.C7);
        var before = event(LocalDate.of(2026, 10, 31), LocalTime.NOON, Nivel.C3);
        var after = event(LocalDate.of(2026, 12, 6), LocalTime.NOON, Nivel.C3);
        event(LocalDate.of(2026, 12, 7), LocalTime.NOON, Nivel.C3);
        var hiddenClub = clubs.save(Club.builder().nombre("Hidden venue").isActive(true).build());
        var hiddenOwner = owners.save(Owner.builder().email(UUID.randomUUID() + "@example.test").password("unused")
                .firstName("Hidden account").lastName("Hidden surname").organizationName("Hidden organization")
                .role(OwnerRole.ORGANIZER).isActive(true).build());
        var hidden = event(day, LocalTime.NOON, Nivel.C3);
        hidden.setClubId(hiddenClub.getId()); hidden.setOwnerId(hiddenOwner.getId());
        hidden.setVisibilidad(TournamentVisibility.SOLO_POR_ENLACE);
        var draft = event(day, LocalTime.NOON, Nivel.C3); draft.setEstado(TournamentStatus.BORRADOR);
        var inactive = event(day, LocalTime.NOON, Nivel.C3); inactive.setIsActive(false);
        events.flush();
        var response = mvc.perform(request("/api/public/calendar").param("month", "2026-11"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.from").value("2026-10-26"))
                .andExpect(jsonPath("$.to").value("2026-12-06"))
                .andExpect(jsonPath("$.events.length()").value(67L))
                .andExpect(jsonPath("$.events[0].id").value(before.getId()))
                .andExpect(jsonPath("$.events[66].id").value(after.getId()))
                                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("Private account name", "private-payment", "private-phone", "ownerId",
                "createdBy", "paymentInstructions", "registrations", "email", "accountName", "Hidden venue", "Hidden organization");
        assertThat(response).contains("Public organization");
    }

    @Test
    void filtersUseSameDeclaredLevelsAsExistingCatalog() throws Exception {
        var range = event(day, LocalTime.of(14, 0), Nivel.C8_C7);
        event(day, LocalTime.of(15, 0), Nivel.C3);
        event(day, LocalTime.of(16, 0), Nivel.C4_C3);
        event(day, LocalTime.of(17, 0), Nivel.TODOS);
        event(day, LocalTime.of(18, 0), Nivel.SUMA_14);
        event(day, LocalTime.of(19, 0), Nivel.SUMA_15);
        mvc.perform(request("/api/public/calendar").param("month", "2026-11").param("level", "C7")
                        .param("clubId", venue.getId().toString()).param("organizerId", organizer.getId().toString())
                        .param("format", "AMERICANO_PAIRS").param("gender", "MASCULINO"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(1L))
                .andExpect(jsonPath("$.events[0].id").value(range.getId()));
        mvc.perform(request("/api/public/catalog").param("clubId", venue.getId().toString()).param("level", "C7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1L));
        mvc.perform(request("/api/public/calendar/catalog").param("level", "C3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(2L));
        for (String level : new String[]{"TODOS", "SUMA_14"}) {
            mvc.perform(request("/api/public/calendar").param("month", "2026-11").param("level", level))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(1L))
                    .andExpect(jsonPath("$.events[0].level").value(level));
        }
    }

    @Test
    void closedAndFullPublicEventsRemainVisibleAndPairCapacityUsesExistingCounts() throws Exception {
        var future = LocalDate.now().plusDays(7);
        var full = event(future, LocalTime.NOON, Nivel.C7); full.setCupoMax(1);
        var captain = player(); var partner = player();
        var one = registration(full, captain, RegistrationStatus.CONFIRMED);
        one.setIsDoubleRegistration(true); one.setMainPlayerId(captain.getId()); one.setPartner(partner);
        var two = registration(full, partner, RegistrationStatus.CONFIRMED);
        two.setIsDoubleRegistration(true); two.setMainPlayerId(captain.getId()); two.setPartner(captain);
        registration(full, player(), RegistrationStatus.SOLO_SEARCH);
        var closed = event(future, LocalTime.of(14, 0), Nivel.C7); closed.setEstado(TournamentStatus.CERRADO);
        events.flush(); registrations.flush();
        mvc.perform(request("/api/public/calendar").param("month", future.toString().substring(0, 7)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(2L))
                .andExpect(jsonPath("$.events[0].capacity").value(1))
                .andExpect(jsonPath("$.events[0].occupied").value(1))
                .andExpect(jsonPath("$.events[0].availability").value("WAITLIST"))
                .andExpect(jsonPath("$.events[1].availability").value("CLOSED"));
    }

    @Test
    void personalStatusesBelongOnlyToCurrentActorAndDisappearAfterCancellation() throws Exception {
        var captain = player(); var partner = player(); var stranger = player();
        var pair = event(day, LocalTime.NOON, Nivel.C7);
        var primary = registration(pair, captain, RegistrationStatus.CONFIRMED);
        primary.setIsDoubleRegistration(true); primary.setMainPlayerId(captain.getId()); primary.setPartner(partner);
        var companion = registration(pair, partner, RegistrationStatus.CONFIRMED);
        companion.setIsDoubleRegistration(true); companion.setMainPlayerId(captain.getId()); companion.setPartner(captain);
        var waitlist = event(day, LocalTime.of(13, 0), Nivel.C7);
        var waiting = registration(waitlist, captain, RegistrationStatus.WAITLIST);
        var solo = event(day, LocalTime.of(14, 0), Nivel.C7);
        registration(solo, captain, RegistrationStatus.SOLO_ADD_LATER);
        var cancelled = event(day, LocalTime.of(15, 0), Nivel.C7);
        registration(cancelled, captain, RegistrationStatus.CANCELLED);
        var hidden = event(day, LocalTime.of(16, 0), Nivel.C7);
        hidden.setVisibilidad(TournamentVisibility.SOLO_POR_ENLACE);
        registration(hidden, captain, RegistrationStatus.CONFIRMED);
        events.flush();
        mvc.perform(request("/api/players/me/calendar").with(user(captain)).param("playerId", stranger.getId().toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.playerId").value(captain.getId()))
                .andExpect(jsonPath("$.registrations.length()").value(3L))
                .andExpect(content().string(containsString("CONFIRMED")))
                .andExpect(content().string(containsString("WAITLIST")))
                .andExpect(content().string(containsString("SOLO_ADD_LATER")));
        mvc.perform(request("/api/players/me/calendar").with(user(partner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registrations.length()").value(1L))
                .andExpect(jsonPath("$.registrations[0].eventId").value(pair.getId()));
        mvc.perform(request("/api/players/me/calendar").with(user(stranger)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registrations.length()").value(0L));
        waiting.setStatus(RegistrationStatus.CANCELLED); waiting.setIsActive(false); registrations.flush();
        mvc.perform(request("/api/players/me/calendar").with(user(captain)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registrations.length()").value(2L));
        mvc.perform(request("/api/players/me/calendar")).andExpect(status().isUnauthorized());
        mvc.perform(request("/api/players/me/calendar").with(user(organizer))).andExpect(status().isForbidden());
    }

    @Test
    void movingAnEventUpdatesTheLivePeriodWithoutDuplicate() throws Exception {
        var moving = event(day, LocalTime.NOON, Nivel.C3);
        moving.setFechaInicio(LocalDate.of(2026, 12, 20)); events.flush();
        mvc.perform(request("/api/public/calendar").param("month", "2026-11"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(0L));
        mvc.perform(request("/api/public/calendar").param("month", "2026-12"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(1L))
                .andExpect(jsonPath("$.events[0].date").value("2026-12-20"));
    }

    @Test
    void invalidFiltersAreLocalizedProblemsAndNoMutationIsPublic() throws Exception {
        for (String lang : new String[]{"ru", "es", "en"}) {
            mvc.perform(request("/api/public/calendar").param("month", "2026-99").param("lang", lang))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("INVALID_CATALOG_FILTER"));
        }
        mvc.perform(request("/api/public/calendar").param("clubId", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(request("/api/public/calendar").param("format", "UNSUPPORTED"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void identicalStartsAndLevelsRemainSeparateWithStableIds() throws Exception {
        var first = event(day, LocalTime.NOON, Nivel.C7);
        var secondVenue = clubs.save(Club.builder().nombre("Second public venue").isActive(true).build());
        var second = event(day, LocalTime.NOON, Nivel.C7); second.setClubId(secondVenue.getId()); events.flush();
        mvc.perform(request("/api/public/calendar").param("month", "2026-11"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(2L))
                .andExpect(jsonPath("$.events[0].id").value(first.getId()))
                .andExpect(jsonPath("$.events[1].id").value(second.getId()))
                .andExpect(jsonPath("$.facets.clubs.length()").value(greaterThanOrEqualTo(2L)));
    }

    private MockHttpServletRequestBuilder request(String path) {
        var builder = get(path).header("Accept", "application/json");
        if (path.startsWith("/api/public/calendar")) {
            builder.param("organizerId", organizer.getId().toString());
        }
        return builder
                .with(request -> { request.setRemoteAddr("198.18.131.10"); return request; });
    }

    private Tournament event(LocalDate date, LocalTime time, Nivel level) {
        return events.saveAndFlush(Tournament.builder().clubId(venue.getId()).ownerId(organizer.getId())
                .createdBy(organizer.getId()).nombre("Public event").fechaInicio(date).horaInicio(time)
                .generoFormato(GenderFormat.MASCULINO).categoriaNivel(level).tipo(TournamentType.AMERICANO_TEAMS)
                .modalidad(Modalidad.DOBLES).cupoMax(16).precio(BigDecimal.ZERO).estado(TournamentStatus.REGISTRO_ABIERTO)
                .isActive(true).contactoOrganizador("private-phone").paymentInstructions("private-payment").build());
    }

    private PlayerPadel player() {
        return players.saveAndFlush(PlayerPadel.builder().nombre("QA").apellido("Calendar")
                .email(UUID.randomUUID() + "@example.test").passwordHash("unused").nivelJugador(Nivel.C7).preferredLocale("es").activo(true).build());
    }

    private TournamentRegistration registration(Tournament event, PlayerPadel actor, RegistrationStatus state) {
        return registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(actor)
                .status(state).isActive(true).registrationDate(LocalDateTime.now()).isDoubleRegistration(false).build());
    }
}
