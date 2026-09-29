package com.padle.core.padelcoreservice.controller.view.americano;


import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.americano.TeamAmericanoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LFPT-0424: GET /tournaments/team-americano/{id}/ranking раньше возвращал несуществующий
 * шаблон tournaments/team-americano/ranking (TemplateInputException → 500). Теперь —
 * 302 на страницу турнира, где рейтинг уже показан (ranking-card). Проверяется через
 * реальную цепочку фильтров Spring Security: роут в permitAll, аноним не уходит на /login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key"
})
public class TeamAmericanoViewControllerRankingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeamAmericanoService teamAmericanoService;
    @MockitoBean
    private TournamentService tournamentService;

    @Test
    void anonimo_ranking_redirigeAPaginaDelTorneo() throws Exception {
        mockMvc.perform(get("/tournaments/team-americano/42/ranking"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/tournaments/team-americano/42"));

        verifyNoInteractions(teamAmericanoService, tournamentService);
    }

    @Test
    void jugador_ranking_redirigeIgual() throws Exception {
        mockMvc.perform(get("/tournaments/team-americano/42/ranking")
                        .with(user("player@test").roles("PLAYER")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/tournaments/team-americano/42"));
    }

    /**
     * Существование турнира в методе не проверяется — целевой роут /{id} разбирается сам.
     */
    @Test
    void torneoInexistente_redirigeSinConsultarBd() throws Exception {
        mockMvc.perform(get("/tournaments/team-americano/999999/ranking"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/tournaments/team-americano/999999"));

        verifyNoInteractions(teamAmericanoService, tournamentService);
    }

    /**
     * Регресс: JSON-рейтинг (используется admin/americano/tournament-double.html) не затронут.
     */
    @Test
    void apiRanking_sigueFuncionando() throws Exception {
        mockMvc.perform(get("/tournaments/team-americano/api/42/ranking")
                        .with(user("player@test").roles("PLAYER")))
                .andExpect(status().isOk());

        verify(teamAmericanoService).getRanking(42L);
    }
}
