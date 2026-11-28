package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.identity.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static com.padle.core.padelcoreservice.support.PlayerFixtures.playerUser;
import static com.padle.core.padelcoreservice.support.PlayerFixtures.principal;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.mail.username=test@example.com", "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test"
})
@AutoConfigureMockMvc
class PlayerContactApiTest {
    @Autowired MockMvc mvc;
    @Autowired PlayerRepository players;
    private final java.util.List<Long> createdPlayers = new java.util.ArrayList<>();

    @AfterEach
    void cleanup() { createdPlayers.forEach(players::deleteById); }

    @Test
    void telegramCannotReplacePhoneAndInvalidTelegramDoesNotSavePhone() throws Exception {
        var player = player();
        mvc.perform(patch("/api/players/me/contact").with(playerUser(player)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"telegramUsername\":\"valid_user\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PHONE_REQUIRED"))
                .andExpect(jsonPath("$.field").value("telefono"));
        mvc.perform(patch("/api/players/me/contact").with(playerUser(player)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"+79125550123\",\"telegramUsername\":\"bad!\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TELEGRAM_INVALID"));
        assertThat(players.findById(player.getId()).orElseThrow().getTelefono()).isNull();
    }

    @Test
    void normalizesNationalPhoneAndStatusReadsFreshProfile() throws Exception {
        var player = player();
        var signedIn = principal(player);
        assertThat(signedIn.isValidContact()).isFalse();
        mvc.perform(patch("/api/players/me/contact").with(user(signedIn)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"8 (912) 555-01-24\",\"phoneCountry\":\"RU\",\"telegramUsername\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.telefono").value("+79125550124"));
        assertThat(player.getTelefono()).isNull();
        assertThat(signedIn.isValidContact()).isFalse();
        mvc.perform(get("/api/players/me/contact-status").with(user(signedIn)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasContact").value(true));
    }

    @Test
    void telegramOnlyProfileStillNeedsPhone() throws Exception {
        var player = player();
        player.setTelegramUsername("@contact_qa_" + player.getId());
        players.saveAndFlush(player);

        mvc.perform(get("/api/players/me/contact-status").with(playerUser(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasContact").value(false))
                .andExpect(jsonPath("$.hasTelegram").value(true));
    }

    @Test
    void duplicatePhoneRollsBackTelegramChange() throws Exception {
        var owner = player();
        owner.setTelefono("+79125550125");
        players.saveAndFlush(owner);
        var player = player();
        mvc.perform(patch("/api/players/me/contact").with(playerUser(player)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"telefono\":\"+7 (912) 555-01-25\",\"telegramUsername\":\"unused_qa_handle\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHONE_IN_USE"));
        var saved = players.findById(player.getId()).orElseThrow();
        assertThat(saved.getTelefono()).isNull();
        assertThat(saved.getTelegramUsername()).isNull();
    }

    private PlayerPadel player() {
        var player = players.saveAndFlush(PlayerPadel.builder().nombre("Contact").apellido("QA")
                .email(UUID.randomUUID() + "@example.test").passwordHash("test-hash")
                .activo(true).emailConfirmado(true).preferredLocale("es").nivelJugador(com.padle.core.application.tournament.Nivel.C9).build());
        createdPlayers.add(player.getId());
        return player;
    }
}
