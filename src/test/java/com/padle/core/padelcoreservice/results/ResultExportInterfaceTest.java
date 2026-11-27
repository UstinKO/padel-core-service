package com.padle.core.padelcoreservice.results;

import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.mail.username=test@example.test","spring.mail.password=test","recaptcha.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=test","spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test","recaptcha.secret-key=test","app.scheduling.enabled=false","telegram.enabled=false",
        "telegram.players.automatic-enabled=false","app.analytics.meta.enabled=false","app.auth.google-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class ResultExportInterfaceTest {
    @Autowired MockMvc mvc;
    @Autowired ResultExportFixtures fixtures;

    @ParameterizedTest @ValueSource(strings={"ADMIN","ORGANIZER","CLUB_ADMIN"})
    void otherAdministratorsDoNotReceiveExportControls(String role) throws Exception {
        var owner=fixtures.account(OwnerRole.valueOf(role),"Qa-only-1503");
        var sample=fixtures.sample("OPEN",owner);
        var html=mvc.perform(get("/admin/tournaments/"+sample.event().getId()).with(user(owner))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("data-results-export-open","id=\"results-export-dialog\"","results-export.js");
    }

    @ParameterizedTest @ValueSource(strings={"ru","es","en"})
    void superAdminGetsLocalizedDialogWithPhonesOffByDefault(String locale) throws Exception {
        var owner=fixtures.account(OwnerRole.SUPER_ADMIN,"Qa-only-1503");
        var sample=fixtures.sample("SETS",owner);
        var html=mvc.perform(get("/admin/tournaments/"+sample.event().getId()).param("lang",locale).with(user(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String label=switch(locale){case "ru"->"Экспорт результатов";case "en"->"Export results";default->"Exportar resultados";};
        assertThat(html).contains("data-results-export-open","id=\"results-export-dialog\"",label,"data-results-export-download disabled");
        assertThat(html).doesNotContain("??results.export.");
        assertThat(html).doesNotContainPattern("data-results-export-contacts[^>]*checked").doesNotContainPattern("data-results-export-ack[^>]*checked");
    }
}
