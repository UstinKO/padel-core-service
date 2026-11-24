package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"telegram.enabled=false", "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class PlayerTelegramPilotIntegrationTest {
    @MockitoBean PlayerTelegramProperties properties;
    @Autowired PlayerRepository players;
    @Autowired PlayerTelegramLinks links;
    @Autowired PlayerTelegramStore store;
    @Autowired MockMvc mvc;

    @Test void pilotRejectsAnotherSessionAndOldTokenButLinksAndDisconnectsTheTester() throws Exception {
        var tester = player();
        var outsider = player();
        when(properties.enabled()).thenReturn(true);
        when(properties.pilotEnabled()).thenReturn(true);
        when(properties.pilotPlayerId()).thenReturn(tester.getId());
        when(properties.botToken()).thenReturn("fixture-token");
        when(properties.botUsername()).thenReturn("OnePadelTestBot");
        mvc.perform(get("/api/players/me/telegram").with(user(outsider)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.available").value(false));
        mvc.perform(post("/api/players/me/telegram/link").with(user(outsider)).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/players/me/telegram/preference").with(user(outsider)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"preference\":\"WHATSAPP\"}"))
                .andExpect(status().isConflict());
        store.token(outsider.getId(), "old-fixture-token", OffsetDateTime.now().plusMinutes(10), "en");
        assertThat(store.bind("old-fixture-token", 123L, 123L)).isEmpty();
        assertThat(store.connection(outsider.getId())).isEmpty();
        var link = links.issue(tester, Locale.forLanguageTag("ru"));
        String token = link.url().substring(link.url().indexOf("start=") + 6);
        assertThat(links.bind(token, 456L, 456L)).contains(tester.getId());
        mvc.perform(get("/api/players/me/telegram").with(user(tester)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.connected").value(true));
        mvc.perform(delete("/api/players/me/telegram").with(user(tester)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(store.connection(tester.getId())).isEmpty();
        assertThat(links.bind(token, 456L, 456L)).isEmpty();
    }

    private PlayerPadel player() {
        return players.saveAndFlush(PlayerPadel.builder().nombre("Pilot").apellido("Fixture")
                .email(UUID.randomUUID() + "@example.test").passwordHash("fixture-hash")
                .activo(true).emailConfirmado(true).build());
    }
}
