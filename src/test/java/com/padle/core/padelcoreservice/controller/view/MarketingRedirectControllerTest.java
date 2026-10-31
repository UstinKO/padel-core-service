package com.padle.core.padelcoreservice.controller.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Короткая ссылка /ig-bio для профиля Instagram.
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",

        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-cli ent-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key"
})
class MarketingRedirectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void igStory_anonimo_redirigeConUtm() throws Exception {
        mockMvc.perform(get("/ig-story").servletPath("/ig-story"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=instagram_stories&utm_content=shared_story"));
    }

    @Test
    void igStory_parametrosExternos_noCambianDestino() throws Exception {
        mockMvc.perform(get("/ig-story").servletPath("/ig-story")
                        .param("url", "https://example.com/")
                        .param("utm_source", "externo"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=instagram_stories&utm_content=shared_story"));
    }

    @Test
    void igBio_anonimo_redirigeConUtm() throws Exception {
        // DispatcherServlet está mapeado a "/" (default servlet) en producción: getServletPath()
        // devuelve la URI completa. MockHttpServletRequest no lo calcula solo — se fija explícitamente.
        mockMvc.perform(get("/ig-bio").servletPath("/ig-bio"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=instagram_profile"));
    }

    @Test
    void octIg_anonimo_siguieFuncionando() throws Exception {
        mockMvc.perform(get("/oct-ig").servletPath("/oct-ig"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(
                        "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=calendar_october"));
    }
}
