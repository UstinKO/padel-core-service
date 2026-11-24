package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import com.padle.core.padelcoreservice.telegram.TelegramModels.ContactPreference;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"telegram.enabled=false", "telegram.players.enabled=true"})
@AutoConfigureMockMvc
@Transactional
class PlayerTelegramStoreIntegrationTest {
    @Autowired PlayerTelegramStore store;
    @Autowired PlayerRepository players;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    private PlayerPadel player() {
        return players.saveAndFlush(PlayerPadel.builder().nombre("Fixture").apellido("Telegram")
                .email(UUID.randomUUID()+"@gmail.com").passwordHash("fixture-hash")
                .nivelJugador(Nivel.C7).preferredLocale("es").activo(true).emailConfirmado(true).build());
    }
    @Test void tokenIsConsumedAndTelegramCannotBeStolenByAnotherProfile() {
        var first = player(); var second = player();
        store.token(first.getId(), "hash-one", OffsetDateTime.now().plusMinutes(10), "es");
        assertThat(store.bind("hash-one", 45678, 45678)).contains(first.getId());
        assertThat(store.bind("hash-one", 45678, 45678)).isEmpty();
        store.token(second.getId(), "hash-two", OffsetDateTime.now().plusMinutes(10), "es");
        assertThat(store.bind("hash-two", 45678, 45678)).isEmpty();
        assertThat(store.connection(first.getId())).isPresent();
        assertThat(store.connection(second.getId())).isEmpty();
        store.disconnect(first.getId());
        assertThat(store.byUser(45678)).isEmpty();
    }
    @Test void expiredLinkAndInactiveProfileCannotBind() {
        var player = player();
        store.token(player.getId(), "expired", OffsetDateTime.now().minusSeconds(1), "es");
        assertThat(store.bind("expired", 56789, 56789)).isEmpty();
        jdbc.update("UPDATE player_padel_db SET activo=false WHERE id=?", player.getId());
        store.token(player.getId(), "inactive", OffsetDateTime.now().plusMinutes(10), "es");
        assertThat(store.bind("inactive", 56789, 56789)).isEmpty();
    }

    @Test void whatsappPersistsPerAccountDisconnectsAndInvalidatesOutstandingLinks() {
        var first = player(); var second = player();
        store.token(first.getId(), "initial", OffsetDateTime.now().plusMinutes(10), "ru");
        assertThat(store.bind("initial", 89123, 89123)).contains(first.getId());
        store.token(first.getId(), "outstanding", OffsetDateTime.now().plusMinutes(10), "en");
        store.preference(first.getId(), ContactPreference.WHATSAPP);
        assertThat(store.preference(first.getId())).isEqualTo(ContactPreference.WHATSAPP);
        assertThat(store.preference(second.getId())).isEqualTo(ContactPreference.UNSPECIFIED);
        assertThat(store.byUser(89123)).isEmpty();
        assertThat(store.connection(first.getId())).isEmpty();
        assertThat(store.bind("outstanding", 89123, 89123)).isEmpty();
        assertThat(store.preferences(List.of(first.getId(), second.getId())))
                .containsEntry(first.getId(), ContactPreference.WHATSAPP).doesNotContainKey(second.getId());
        assertThat(jdbc.queryForObject("SELECT preferred_locale FROM player_padel_db WHERE id=?", String.class, first.getId()))
                .isEqualTo("en");
        store.token(first.getId(), "new-link", OffsetDateTime.now().plusMinutes(10), "es");
        assertThat(store.bind("new-link", 89123, 89123)).contains(first.getId());
        assertThat(store.preference(first.getId())).isEqualTo(ContactPreference.TELEGRAM);
    }

    @Test void preferenceEndpointUsesSessionIdentityAndRequiresCsrfAndAValidValue() throws Exception {
        var first = player(); var second = player();
        String url = "/api/players/me/telegram/preference";
        mvc.perform(put(url).with(user(first)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"preference\":\"WHATSAPP\"}")).andExpect(status().isForbidden());
        mvc.perform(put(url).with(user(first)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"preference\":\"INVALID\"}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put(url).with(user(first)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(put(url).with(user(first)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"preference\":\"WHATSAPP\",\"playerId\":" + second.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preference").value("WHATSAPP"))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(store.preference(first.getId())).isEqualTo(ContactPreference.WHATSAPP);
        assertThat(store.preference(second.getId())).isEqualTo(ContactPreference.UNSPECIFIED);
        mvc.perform(get("/api/admin/tournaments/42/telegram").with(user(first)))
                .andExpect(status().isForbidden());
    }
}
