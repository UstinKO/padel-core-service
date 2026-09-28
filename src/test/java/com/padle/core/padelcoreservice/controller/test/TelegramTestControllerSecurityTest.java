package com.padle.core.padelcoreservice.controller.test;

import com.padle.core.padelcoreservice.service.TelegramReminderScheduler;
import com.padle.core.padelcoreservice.service.TelegramService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LFPT-0423: /test/telegram/** раньше был в permitAll и в CSRF-исключениях — любой анонимный
 * запрос мог слать сообщения в рабочую группу и напоминания игрокам. Проверяет правила доступа
 * через реальную цепочку фильтров Spring Security (MockMvc), Telegram-сервисы замоканы —
 * ни одно сообщение не уходит.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key",
})
class TelegramTestControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelegramService telegramService;
    @MockitoBean
    private TelegramReminderScheduler reminderScheduler;

    @Test
    void anonimo_noPuedeEnviarMensajes_niVerEstado() throws Exception {
        mockMvc.perform(post("/test/telegram/ping").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(post("/test/telegram/reminders/force/1").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/test/telegram/status"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        verifyNoInteractions(telegramService, reminderScheduler);
    }

    @Test
    void jugador_esRechazado() throws Exception {
        mockMvc.perform(post("/test/telegram/reminders").with(csrf()).with(user("player@test").roles("PLAYER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));

        verifyNoInteractions(reminderScheduler);
    }

    @Test
    void adminNoSuperAdmin_esRechazado() throws Exception {
        for (String role : new String[]{"ORGANIZER", "ADMIN", "CLUB_ADMIN"}) {
            mockMvc.perform(post("/test/telegram/ping").with(csrf()).with(user("owner@test").roles(role)))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/error/403"));
        }

        verify(telegramService, never()).sendMessage(anyString());
    }

    @Test
    void superAdmin_sinCsrf_esRechazado() throws Exception {
        mockMvc.perform(post("/test/telegram/ping").with(user("root@test").roles("SUPER_ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
        mockMvc.perform(post("/test/telegram/reminders/force/7").with(user("root@test").roles("SUPER_ADMIN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));

        verify(telegramService, never()).sendMessage(anyString());
        verify(reminderScheduler, never()).sendReminderByTournamentId(anyLong());
    }

    @Test
    void superAdmin_conCsrf_funcionaComoAntes() throws Exception {
        mockMvc.perform(post("/test/telegram/ping").with(csrf()).with(user("root@test").roles("SUPER_ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/test/telegram/reminders/force/7").with(csrf()).with(user("root@test").roles("SUPER_ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/test/telegram/status").with(user("root@test").roles("SUPER_ADMIN")))
                .andExpect(status().isOk());

        verify(telegramService).sendMessage(anyString());
        verify(reminderScheduler).sendReminderByTournamentId(7L);
    }
}
