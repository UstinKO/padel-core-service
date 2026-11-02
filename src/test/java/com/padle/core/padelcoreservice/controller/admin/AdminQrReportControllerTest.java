package com.padle.core.padelcoreservice.controller.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
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
class AdminQrReportControllerTest {
    @Autowired private MockMvc mvc;

    @ParameterizedTest
    @ValueSource(strings = {"es", "ru", "en"})
    void rendersTranslatedReportForSuperAdmin(String language) throws Exception {
        mvc.perform(get("/admin/marketing/qr").param("lang", language)
                        .header("Accept-Language", language).with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk()).andExpect(view().name("admin/qr-report"))
                .andExpect(content().string(containsString("id=\"qr-filter-form\"")))
                .andExpect(xpath("//script[starts-with(@src, '/js/qr-report') and @defer]").exists())
                .andExpect(content().string(containsString("15cero Saavedra")))
                .andExpect(content().string(containsString(switch (language) {
                    case "ru" -> "Переходы по QR";
                    case "en" -> "QR openings";
                    default -> "Aperturas por QR";
                })));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLAYER", "OWNER", "ORGANIZER", "ADMIN", "CLUB_ADMIN"})
    void reportRequiresSuperAdmin(String role) throws Exception {
        mvc.perform(get("/admin/marketing/qr").with(user("other").roles(role)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/error/403"));
    }

    @Test
    void guestIsAskedToSignIn() throws Exception {
        mvc.perform(get("/admin/marketing/qr")).andExpect(status().is3xxRedirection());
    }
}
