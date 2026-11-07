package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.model.enums.TournamentVisibility;
import com.padle.core.padelcoreservice.service.TournamentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com", "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key", "recaptcha.secret-key=test-secret-key",
        "app.base-url=https://1-padel.com"
})
class TournamentCalendarControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private TournamentService tournaments;
    private TournamentDto event;

    @BeforeEach
    void setup() {
        event = TournamentDto.builder().id(42L).tipo(TournamentType.AMERICANO).categoriaNivel("C8")
                .modalidad(Modalidad.DOBLES).fechaInicio(LocalDate.of(2026, 11, 2)).horaInicio(LocalTime.of(18, 30))
                .durationMinutes(90).clubNombre("Club Ñandú").clubDireccion("Córdoba 123")
                .visibilidad(TournamentVisibility.SOLO_POR_ENLACE).build();
        when(tournaments.getActiveTournamentById(42L)).thenReturn(Optional.of(event));
        when(tournaments.getActiveTournamentById(999L)).thenReturn(Optional.empty());
    }

    @Test
    void anonymousInlineAndDownloadHaveSameFileAndNoMutations() throws Exception {
        byte[] inline = mvc.perform(get("/api/public/tournaments/42/calendar/event.ics?lang=ru"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/calendar;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"1-padel-42.ics\""))
                .andExpect(content().string(containsString("DTSTART:20261102T213000Z")))
                .andReturn().getResponse().getContentAsByteArray();
        mvc.perform(get("/api/public/tournaments/42/calendar/event.ics?lang=ru&download=true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"1-padel-42.ics\""))
                .andExpect(content().bytes(inline));
        verify(tournaments, times(2)).getActiveTournamentById(42L);
        verifyNoMoreInteractions(tournaments);
    }

    @Test
    void googleStillOpensTemplateAndDoesNotCreateEvent() throws Exception {
        mvc.perform(get("/api/public/tournaments/42/calendar/google?lang=es"))
                .andExpect(status().isFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Location", startsWith("https://calendar.google.com/calendar/render?action=TEMPLATE&")));
        mvc.perform(get("/api/public/tournaments/42/calendar?lang=en"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.googleUrl").value(containsString("Americano"), String.class));
    }

    @Test
    void missingDataAndInactiveEventsDoNotGenerateMisleadingFiles() throws Exception {
        event.setDurationMinutes(null);
        mvc.perform(get("/api/public/tournaments/42/calendar/event.ics?lang=ru"))
                .andExpect(status().isUnprocessableContent()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/public/tournaments/999/calendar/event.ics"))
                .andExpect(status().isNotFound());
    }
}
