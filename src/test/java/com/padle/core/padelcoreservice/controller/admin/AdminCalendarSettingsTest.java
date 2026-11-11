package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com", "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key", "recaptcha.secret-key=test-secret-key"
})
class AdminCalendarSettingsTest {
    @Autowired private MockMvc mvc;
    @Autowired private TournamentRepository tournaments;
    @Autowired private ClubRepository clubs;
    @Autowired private EntityManager entities;
    private Tournament event;
    private Owner owner;

    @BeforeEach
    void setup() {
        owner = Owner.builder().id(999L).email("calendar-test@example.com").role(OwnerRole.SUPER_ADMIN).build();
        Club club = clubs.save(Club.builder().nombre("Calendar " + UUID.randomUUID()).build());
        event = tournaments.save(Tournament.builder().clubId(club.getId()).nombre("Test")
                .fechaInicio(LocalDate.of(2030, 12, 15)).horaInicio(LocalTime.of(18, 30)).duracion("4 horas")
                .durationMinutes(90).generoFormato(GenderFormat.MASCULINO).categoriaNivel(Nivel.C7)
                .tipo(TournamentType.KING_OF_COURT).modalidad(Modalidad.INDIVIDUAL).cupoMax(16)
                .precio(BigDecimal.ZERO).contactoOrganizador("test-only").build());
    }

    @Test
    void settingsRenderAndSaveExactDurationAndZone() throws Exception {
        mvc.perform(get("/admin/tournaments/" + event.getId() + "/edit?lang=ru").with(user(owner)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Точная длительность")));
        Long currentClubId = event.getClubId();
        mvc.perform(editRequest(owner)).andExpect(status().is3xxRedirection());
        Tournament saved = tournaments.findById(event.getId()).orElseThrow();
        assertThat(saved.getDurationMinutes()).isEqualTo(105);
        assertThat(saved.getTimeZone()).isEqualTo("Europe/Madrid");
        assertThat(saved.getClubId()).isEqualTo(currentClubId);
    }

    @Test
    void savesSelectedClubAndDisplaysItOnThePublicClinicPage() throws Exception {
        event.setTipo(TournamentType.PADEL_CLINIC);
        var newClub = clubs.save(Club.builder().nombre("Nueva sede " + UUID.randomUUID()).build());
        Long eventId = event.getId();

        mvc.perform(editRequest(owner).param("clubId", newClub.getId().toString()))
                .andExpect(redirectedUrl("/admin/tournaments/" + eventId));
        entities.flush();
        entities.clear();

        assertThat(tournaments.findById(eventId).orElseThrow().getClubId()).isEqualTo(newClub.getId());
        mvc.perform(get("/admin/tournaments/" + eventId + "/edit").with(user(owner)))
                .andExpect(model().attribute("tournament", hasProperty("clubId", is(newClub.getId()))));
        mvc.perform(get("/torneo/" + eventId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(newClub.getNombre())));
    }

    @Test
    void clubAdminCannotMoveEventToAnotherClub() throws Exception {
        var otherClub = clubs.save(Club.builder().nombre("Otro club " + UUID.randomUUID()).build());
        var clubAdmin = Owner.builder().id(998L).email("club-test@example.com").role(OwnerRole.CLUB_ADMIN)
                .clubId(event.getClubId()).build();
        Long originalClubId = event.getClubId();

        mvc.perform(editRequest(clubAdmin).param("clubId", otherClub.getId().toString()))
                .andExpect(redirectedUrl("/admin/tournaments"));
        assertThat(event.getClubId()).isEqualTo(originalClubId);
        assertThat(event.getDurationMinutes()).isEqualTo(90);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void invalidDestinationDoesNotSaveAnyChanges(boolean inactiveClubExists) throws Exception {
        Long badClubId = inactiveClubExists
                ? clubs.save(Club.builder().nombre("Club inactivo " + UUID.randomUUID())
                        .isActive(false).build()).getId()
                : Long.MAX_VALUE;
        Long originalClubId = event.getClubId();

        mvc.perform(editRequest(owner).param("clubId", badClubId.toString()))
                .andExpect(redirectedUrl("/admin/tournaments/" + event.getId() + "/edit"));
        assertThat(event.getClubId()).isEqualTo(originalClubId);
        assertThat(event.getDurationMinutes()).isEqualTo(90);
    }

    private MockHttpServletRequestBuilder editRequest(Owner editor) {
        return post("/admin/tournaments/" + event.getId() + "/edit").with(user(editor)).with(csrf())
                .param("fechaInicio", "2030-12-15").param("horaInicio", "18:30")
                .param("duracion", "4 horas").param("durationMinutes", "105").param("timeZone", "Europe/Madrid")
                .param("generoFormato", "MASCULINO").param("categoriaNivel", "C7").param("tipo", event.getTipo().name())
                .param("modalidad", "INDIVIDUAL").param("cupoMax", "16").param("precio", "0")
                .param("moneda", "ARS").param("estado", "REGISTRO_ABIERTO").param("contactoOrganizador", "test-only");
    }

    @Test
    void suggestsMinutesUsingCalendarRules() throws Exception {
        mvc.perform(get("/admin/tournaments/calendar-duration").with(user(owner))
                        .param("description", "3 - 4 horas"))
                .andExpect(status().isOk()).andExpect(content().string("240"));
        mvc.perform(get("/admin/tournaments/calendar-duration").with(user(owner))
                        .param("description", "varias horas"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/admin/tournaments/calendar-duration").param("description", "3 horas"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void invalidSettingsPreserveFormChoicesAndStoredData() throws Exception {
        mvc.perform(post("/admin/tournaments/" + event.getId() + "/edit?lang=en").with(user(owner)).with(csrf())
                        .param("durationMinutes", "0").param("timeZone", "wrong/zone"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("tournament", "durationMinutes", "calendarTimeZoneValid"))
                .andExpect(model().attributeExists("modalidades", "visibilidades", "isClubAdmin"))
                .andExpect(content().string(containsString("Enter a valid IANA time zone.")));
        assertThat(tournaments.findById(event.getId()).orElseThrow().getDurationMinutes()).isEqualTo(90);
    }
}
