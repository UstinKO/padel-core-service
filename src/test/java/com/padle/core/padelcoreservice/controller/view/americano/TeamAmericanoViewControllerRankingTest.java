package com.padle.core.padelcoreservice.controller.view.americano;


import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.americano.TeamAmericanoService;
import org.junit.jupiter.api.Test;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoRankingDto;
import com.padle.core.padelcoreservice.dto.americano.RoundRobinStandingDto;
import com.padle.core.padelcoreservice.dto.americano.RoundRobinAdditionalMatchDto;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /tournaments/team-americano/{id}/ranking — 302 на страницу турнира, где рейтинг уже
 * показан (ranking-card): отдельного шаблона рейтинга нет. Проверяется через реальную
 * цепочку фильтров Spring Security: роут в permitAll, аноним не уходит на /login.
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

    @ParameterizedTest
    @CsvSource({
            "ru,POINTS,Итоговые места", "es,POINTS,Puestos finales", "en,POINTS,Final places",
            "ru,SETS,Итоговые места", "es,SETS,Puestos finales", "en,SETS,Final places"
    })
    void publicResultsKeepFinalPlacesSeparateAndHideControls(String language, String mode, String title) throws Exception {
        var event = TournamentDto.builder()
                .id(999999L).nombre("Round Robin QA")
                .estado(TournamentStatus.FINALIZADO)
                .mostrarNivel(true).build();
        var team = AmericanoTeamDto.builder()
                .id(1L).teamNumber(7).currentPosition(1).displayName("Alpha / Beta")
                .totalScore(36).matchesPlayed(3).matchesWon(2).matchesLost(1).pointDifference(4).gameDifference(4)
                .player1Email("hidden@example.test").player1Phone("private-contact").build();
        var standing = new RoundRobinStandingDto(1L, 7, "Alpha / Beta", 1);
        var extra = new RoundRobinAdditionalMatchDto(
                11L, 1, null, 1, 1L, 7, "Alpha / Beta", 2L, 8, "Gamma / Delta", 6, 4,
                AmericanoRoundStatus.COMPLETED);
        var ranking = TeamAmericanoRankingDto.builder()
                .scoringMode(RoundRobinScoringMode.valueOf(mode))
                .ranking(List.of(team)).finalRanking(List.of(standing))
                .additionalMatches(List.of(extra)).totalTeams(2).courts(1).pointsPerMatch(32)
                .roundRobinComplete(true).isFinished(true).build();
        when(tournamentService.getTournamentDtoById(999999L)).thenReturn(Optional.of(event));
        when(teamAmericanoService.isInitialized(999999L)).thenReturn(true);
        when(teamAmericanoService.getRanking(999999L)).thenReturn(ranking);

        mockMvc.perform(get("/tournaments/team-americano/999999").param("lang", language))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString(title)))
                .andExpect(content().string(
                        containsString("<span class=\"rr-place\">1.</span>")))
                .andExpect(content().string(
                        not(containsString("data-rr-score"))))
                .andExpect(content().string(
                        not(containsString("data-rr-add"))))
                .andExpect(content().string(
                        not(containsString("hidden@example.test"))));
    }

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
