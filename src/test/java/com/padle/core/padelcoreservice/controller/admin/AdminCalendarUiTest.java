package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class AdminCalendarUiTest {
    @Autowired MockMvc mvc;

    @ParameterizedTest
    @CsvSource({"ru,Календарь мероприятий,Все доступные клубы", "es,Calendario de eventos,Todas las sedes disponibles", "en,Event calendar,All available venues"})
    void calendarShellAndFiltersTranslateForEveryLanguage(String language, String title, String venues) throws Exception {
        Owner owner = Owner.builder().id(-1263L).email("calendar-ui@example.test").firstName("Calendar")
                .lastName("QA").role(OwnerRole.ORGANIZER).build();
        mvc.perform(get("/admin/tournaments").param("view","calendar").param("month","2026-12")
                .param("level","TODOS").param("format","PADEL_CLINIC").param("lang",language).with(user(owner)))
            .andExpect(status().isOk()).andExpect(content().string(containsString(title)))
            .andExpect(content().string(containsString(venues)))
            .andExpect(content().string(not(containsString("??admin.calendar."))))
            .andExpect(model().attribute("resultCount",0));
    }
}
