package com.padle.core.padelcoreservice.config;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.repository.OwnerRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.base-url=https://configured.example/padel///",
        "spring.mail.username=test@example.invalid",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test",
        "recaptcha.secret-key=test"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PageModelAdviceApplicationTest {

    private static final String EXPECTED_BASE_URL =
            "https://configured.example/padel";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private OwnerRepository ownerRepository;

    @Test
    void shouldAddBaseUrlToPublicPage() throws Exception {
        mockMvc.perform(get("/terminos"))
                .andExpect(status().isOk())
                .andExpect(view().name("legal/terminos"))
                .andExpect(model().attribute("year", Year.now().getValue()))
                .andExpect(model().attribute("siteBaseUrl", EXPECTED_BASE_URL))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("</html>")));
    }

    @Test
    void shouldAddBaseUrlToPlayerPage() throws Exception {
        var player = playerRepository.saveAndFlush(PlayerPadel.builder()
                .nombre("QAPlayer")
                .apellido("Advice")
                .email(UUID.randomUUID() + "@example.invalid")
                .passwordHash("{noop}unused")
                .activo(true)
                .emailConfirmado(true)
                .nivelJugador(Nivel.SIN_ESPECIFICAR)
                .preferredLocale("es")
                .build());

        mockMvc.perform(get("/perfil").with(user(player)))
                .andExpect(status().isOk())
                .andExpect(view().name("players/perfil"))
                .andExpect(model().attribute("player", player))
                .andExpect(model().attribute("siteBaseUrl", EXPECTED_BASE_URL))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("QAPlayer")))
                .andExpect(content().string(containsString("</html>")));
    }

    @Test
    void shouldAddBaseUrlToAdminPage() throws Exception {
        var owner = ownerRepository.saveAndFlush(Owner.builder()
                .firstName("QAAdmin")
                .lastName("Advice")
                .email(UUID.randomUUID() + "@example.invalid")
                .password("{noop}unused")
                .role(OwnerRole.SUPER_ADMIN)
                .isActive(true)
                .build());

        mockMvc.perform(get("/admin").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/panel"))
                .andExpect(model().attribute("adminName", "QAAdmin"))
                .andExpect(model().attribute("siteBaseUrl", EXPECTED_BASE_URL))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("</html>")));
    }
}