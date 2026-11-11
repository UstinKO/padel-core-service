package com.padle.core.padelcoreservice.controller.test;

import com.padle.core.padelcoreservice.service.TournamentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Locale;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistrationCancellationControllerTest {
    private TournamentService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(TournamentService.class);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new RegistrationCancellationController(service, messages)).build();
    }

    @ParameterizedTest
    @CsvSource({"ru,Не удалось отменить участие", "es,No se pudo cancelar la participación", "en,Unable to cancel participation"})
    void failureIsLocalizedAndDoesNotExposeDatabaseDetails(String language, String expected) throws Exception {
        doThrow(new DataIntegrityViolationException("DELETE SQL marketing_registrations_db_registration_id_fkey"))
                .when(service).adminRemovePlayerRegistration(eq(1L), eq(2L), any());
        mvc.perform(post("/test/tournaments/1/remove-player/2").locale(Locale.forLanguageTag(language)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(content().string(containsString(expected)))
                .andExpect(content().string(not(containsString("SQL"))))
                .andExpect(content().string(not(containsString("marketing_registrations_db"))));
    }

    @Test
    void successRetainsResponseContract() throws Exception {
        mvc.perform(post("/test/tournaments/1/remove-player/2").locale(Locale.forLanguageTag("ru")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Участие отменено."));
        verify(service).adminRemovePlayerRegistration(eq(1L), eq(2L), any());
    }
}
