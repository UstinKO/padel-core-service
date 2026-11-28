package com.padle.core.padelcoreservice.telegram;

import com.padle.core.application.tournament.Nivel;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.telegram.TelegramModels.ContactPreference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

import static com.padle.core.padelcoreservice.support.PlayerFixtures.playerUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"telegram.enabled=false", "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class TelegramConnectionStatisticsIntegrationTest {
    @Autowired PlayerRepository players;
    @Autowired PlayerTelegramStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean PlayerTelegramProperties properties;
    @MockitoBean PlayerTelegramApi api;

    @Test void statisticsAreDisjointIgnoreInactiveAndDoNotDependOnSendingFlags() {
        var before = store.connectionStatistics();
        var connected = player(false, true);
        var declined = player(false, true);
        var whatsapp = player(false, true);
        var disabled = player(false, true);
        var ordinary = player(false, true);
        player(true, true);
        player(false, false);
        store.invitationDismissed(connected.getId(), true);
        store.invitationDismissed(declined.getId(), true);
        store.invitationDismissed(whatsapp.getId(), true);
        store.preference(whatsapp.getId(), ContactPreference.WHATSAPP);
        connection(connected, true);
        connection(disabled, false);
        var result = store.connectionStatistics();
        assertThat(result.registered()-before.registered()).isEqualTo(5);
        assertThat(result.connected()-before.connected()).isEqualTo(1);
        assertThat(result.notConnected()-before.notConnected()).isEqualTo(2);
        assertThat(result.whatsapp()-before.whatsapp()).isEqualTo(1);
        assertThat(result.declined()-before.declined()).isEqualTo(1);
        assertThat(result.guests()-before.guests()).isEqualTo(1);
        assertThat(result.registered()).isEqualTo(result.connected()+result.notConnected()+result.whatsapp()+result.declined());
        assertThat(store.invitationDismissed(ordinary.getId())).isFalse();
        verifyNoInteractions(api);
    }

    @Test void ownInvitationChoiceSurvivesReadsAndPreservesChannelAndConnection() throws Exception {
        when(properties.enabled()).thenReturn(true);
        when(properties.pilotEnabled()).thenReturn(false);
        var player = player(false, true);
        store.preference(player.getId(), ContactPreference.WHATSAPP);
        invitation(player, true);
        mvc.perform(get("/api/players/me/telegram").with(playerUser(player)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.invitationDismissed").value(true))
                .andExpect(jsonPath("$.preference").value("WHATSAPP"));
        invitation(player, false);
        assertThat(store.invitationDismissed(player.getId())).isFalse();
        store.preference(player.getId(), ContactPreference.TELEGRAM);
        connection(player, true);
        var saved = store.connection(player.getId());
        invitation(player, true);
        assertThat(store.connection(player.getId())).isEqualTo(saved);
        verify(api, never()).send(anyLong(), anyString(), anyList());
    }

    @Test void invitationRequiresOwnPlayerCsrfAndExplicitBoolean() throws Exception {
        when(properties.enabled()).thenReturn(true);
        var player = player(false, true);
        mvc.perform(put("/api/players/me/telegram/invitation").with(playerUser(player))
                .contentType(MediaType.APPLICATION_JSON).content("{\"dismissed\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/players/me/telegram/invitation").with(playerUser(player)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(put("/api/players/me/telegram/invitation").with(playerUser(player(true,true))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"dismissed\":true}"))
                .andExpect(status().isConflict());
        assertThat(store.invitationDismissed(player.getId())).isFalse();
    }

    @Test void statisticsArePrivateNoStoreAndReadOnly() throws Exception {
        var before = store.connectionStatistics();
        mvc.perform(get("/api/admin/telegram/connections").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.registered").value(before.registered()))
                .andExpect(jsonPath("$.guests").value(before.guests()))
                .andExpect(jsonPath("$.userId").doesNotExist());
        for (String role : new String[]{"PLAYER","OWNER","ORGANIZER","CLUB_ADMIN"}) {
            mvc.perform(get("/api/admin/telegram/connections").with(user("other").roles(role)))
                    .andExpect(status().isForbidden());
        }
        assertThat(store.connectionStatistics()).isEqualTo(before);
        verifyNoInteractions(api);
    }

    private void invitation(PlayerPadel player, boolean dismissed) throws Exception {
        mvc.perform(put("/api/players/me/telegram/invitation").with(playerUser(player)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"dismissed\":"+dismissed+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.invitationDismissed").value(dismissed));
    }

    private void connection(PlayerPadel player, boolean enabled) {
        long userId = 7_777_000_000L+player.getId();
        jdbc.update("INSERT INTO player_telegram_connections(player_id,user_id,chat_id,enabled) VALUES (?,?,?,?)",
                player.getId(), userId, userId, enabled);
    }

    private PlayerPadel player(boolean guest, boolean active) {
        return players.saveAndFlush(PlayerPadel.builder().nombre("Stats").apellido("Fixture")
                .email(UUID.randomUUID()+(guest?"@1padel.guest":"@example.test"))
                .passwordHash("fixture").preferredLocale("es").nivelJugador(Nivel.C9).activo(active).emailConfirmado(true).build());
    }
}
