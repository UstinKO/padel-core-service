package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.OwnerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.service.ClubAccountService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.calendar.EventSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AdminTournamentOrderIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private OwnerRepository owners;
    @Autowired private ClubRepository clubs;
    @Autowired private TournamentRepository events;
    @Autowired private TournamentService tournaments;
    @Autowired private ClubAccountService accounts;

    private Club venue;
    private Owner root;
    private Owner club;
    private Owner other;
    private LocalDate day;

    @BeforeEach
    void prepare() {
        venue = clubs.save(Club.builder().nombre("QA Order " + UUID.randomUUID()).isActive(true).build());
        root = owner(OwnerRole.SUPER_ADMIN);
        club = owner(OwnerRole.CLUB_ADMIN);
        other = owner(OwnerRole.ORGANIZER);
        accounts.assign(root, club.getId(), Set.of(venue.getId()));
        day = LocalDate.now(EventSchedule.zone(null)).plusDays(2);
    }

    @Test
    void clubAndMainListShareOrderAndRetainPastAndCancelledEvents() throws Exception {
        var late = event(club, day, "18:00", TournamentStatus.REGISTRO_ABIERTO);
        var next = event(club, day.plusDays(1), "15:00", TournamentStatus.REGISTRO_ABIERTO);
        var early = event(club, day, "14:00", TournamentStatus.REGISTRO_ABIERTO);
        var cancelled = event(club, day.plusDays(2), "13:00", TournamentStatus.CANCELADO);
        var past = event(club, day.minusDays(3), "14:00", TournamentStatus.FINALIZADO);
        var old = event(club, day.minusDays(4), "18:00", TournamentStatus.FINALIZADO);
        List<Long> expected = ids(early, late, next, cancelled, past, old);

        assertThat(list(club)).isEqualTo(expected);
        assertThat(list(root)).isEqualTo(expected);
        assertThat(tournaments.getTournamentsForOwner(club.getId(), false)).extracting(TournamentDto::getId)
                .containsExactlyElementsOf(expected);
        assertThat(list(club)).isEqualTo(expected);
    }

    @Test
    void combinedFiltersAndResetKeepOrderWithoutRevealingOtherOrganizerAtSameVenue() throws Exception {
        var late = event(club, day, "18:00", TournamentStatus.REGISTRO_ABIERTO);
        var early = event(club, day, "14:00", TournamentStatus.REGISTRO_ABIERTO);
        var foreign = event(other, day, "12:00", TournamentStatus.REGISTRO_ABIERTO);

        assertThat(list(club, "format", "AMERICANO_PAIRS", "level", "C6", "status", "REGISTRO_ABIERTO",
                "from", day.toString(), "to", day.toString())).containsExactly(early.getId(), late.getId());
        assertThat(list(club, "organizerId", other.getId().toString())).isEmpty();
        assertThat(list(club)).containsExactly(early.getId(), late.getId());
        assertThat(list(root)).containsExactly(foreign.getId(), early.getId(), late.getId());
        mvc.perform(get("/admin/tournaments/" + foreign.getId()).with(user(club))
                .with(request -> { request.setRemoteAddr("198.18.130.6"); return request; })
                .header("Accept", "application/json")).andExpect(status().isForbidden());
    }

    @Test
    void creationAndDateOrTimeEditsReorderNextRead() throws Exception {
        var early = event(club, day, "14:00", TournamentStatus.REGISTRO_ABIERTO);
        var late = event(club, day, "18:00", TournamentStatus.REGISTRO_ABIERTO);
        var created = tournaments.createTournament(form(day.plusDays(1), "13:00"), club.getId());
        assertThat(list(club)).containsExactly(early.getId(), late.getId(), created.getId());

        tournaments.updateTournament(created.getId(), form(day, "12:00"), club).orElseThrow();
        assertThat(list(club)).containsExactly(created.getId(), early.getId(), late.getId());
        tournaments.updateTournament(created.getId(), form(day, "16:00"), club).orElseThrow();
        assertThat(list(club)).containsExactly(early.getId(), created.getId(), late.getId());
        assertThat(list(root)).containsExactly(early.getId(), created.getId(), late.getId());
    }

    private List<Long> list(Owner actor, String... filters) throws Exception {
        var request = get("/admin/tournaments").with(user(actor)).param("clubId", venue.getId().toString())
                .with(value -> { value.setRemoteAddr("198.18.130.6"); return value; });
        for (int i = 0; i < filters.length; i += 2) {
            request.param(filters[i], filters[i + 1]);
        }
        var response = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var model = response.getModelAndView().getModel();
        return ((List<?>) model.get("tournaments")).stream()
                .map(row -> ((TournamentDto) ((Map<?, ?>) row).get("tournament")).getId()).toList();
    }

    private TournamentDto form(LocalDate date, String time) {
        return TournamentDto.builder().clubId(venue.getId()).fechaInicio(date).horaInicio(LocalTime.parse(time))
                .generoFormato(GenderFormat.MASCULINO).categoriaNivel("C6").tipo(TournamentType.AMERICANO_TEAMS)
                .modalidad(Modalidad.DOBLES).cupoMax(8).precio(BigDecimal.ZERO).moneda("ARS")
                .estado(TournamentStatus.REGISTRO_ABIERTO).contactoOrganizador("QA").build();
    }

    private Tournament event(Owner actor, LocalDate date, String time, TournamentStatus state) {
        return events.saveAndFlush(Tournament.builder().ownerId(actor.getId()).createdBy(actor.getId())
                .clubId(venue.getId()).nombre("QA " + UUID.randomUUID()).fechaInicio(date)
                .horaInicio(LocalTime.parse(time)).generoFormato(GenderFormat.MASCULINO).categoriaNivel(Nivel.C6)
                .tipo(TournamentType.AMERICANO_TEAMS).modalidad(Modalidad.DOBLES).cupoMax(8)
                .precio(BigDecimal.ZERO).estado(state).contactoOrganizador("QA").isActive(true).build());
    }

    private Owner owner(OwnerRole role) {
        return owners.saveAndFlush(Owner.builder().email(UUID.randomUUID() + "@example.test")
                .password("synthetic-only").firstName("QA").lastName("Order").role(role).isActive(true).build());
    }

    private static List<Long> ids(Tournament... events) {
        return Arrays.stream(events).map(Tournament::getId).toList();
    }
}
