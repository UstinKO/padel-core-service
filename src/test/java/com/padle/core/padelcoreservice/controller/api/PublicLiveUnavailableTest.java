package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.service.PublicLiveService;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Locale;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

class PublicLiveUnavailableTest {
    @Test void unavailableDatabaseProducesSafeLocalizedProblem() throws Exception {
        var service=mock(PublicLiveService.class);
        when(service.snapshot(42)).thenThrow(new DataAccessResourceFailureException("private SQL credential"));
        var messages=new StaticMessageSource();
        messages.addMessage("public.live.LIVE_DATA_UNAVAILABLE",Locale.forLanguageTag("ru"),"Данные недоступны");
        var mvc=MockMvcBuilders.standaloneSetup(new PublicLiveController(service))
                .setControllerAdvice(new PublicLiveExceptionHandler(messages)).build();
        var body=mvc.perform(get("/api/public/events/42/live").locale(Locale.forLanguageTag("ru")))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.code").value("LIVE_DATA_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").value("Данные недоступны"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("SQL","credential","DataAccess");
    }
}
