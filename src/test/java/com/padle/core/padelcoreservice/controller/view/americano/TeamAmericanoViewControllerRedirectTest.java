package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoRankingDto;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.americano.TeamAmericanoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
public class TeamAmericanoViewControllerRedirectTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeamAmericanoService teamAmericanoService;
    @MockitoBean
    private TournamentService tournamentService;
    @MockitoBean
    private TournamentAccessService tournamentAccessService;

    @Test
    void initialize_exito_redirigeAAdminTeamAmericano() throws Exception {
        when(teamAmericanoService.isInitialized(42L)).thenReturn(false);

        when(tournamentService.getActiveTournamentById(42L)).thenReturn(Optional.of(

                TournamentDto.builder().id(42L).estado(TournamentStatus.CERRADO).build()));

        mockMvc.perform(post("/tournaments/team-americano/initialize")
                        .param("tournamentId", "42")
                        .param("courts", "2")
                        .param("pointsPerMatch", "32")
                        .with(user("admin@test").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/tournaments/team-americano/admin/42"))
                .andExpect(flash().attribute("success",
                        "¡Team Americano inicializado! Todas las parejas tienen su fixture."));
    }

    @Test
    void initialize_yaInicializado_redirigeIgual() throws Exception {
        when(teamAmericanoService.isInitialized(42L)).thenReturn(true);

        mockMvc.perform(post("/tournaments/team-americano/initialize")
                        .param("tournamentId", "42")
                        .with(user("admin@test").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/tournaments/team-americano/admin/42"));
    }

    @Test
    void initialize_error_redirigeATarjetaDelTorneo() throws Exception {
        when(teamAmericanoService.isInitialized(42L)).thenReturn(false);
        when(tournamentService.getActiveTournamentById(42L)).thenReturn(Optional.of(
                TournamentDto.builder().id(42L).estado(TournamentStatus.CERRADO).build()));
        when(teamAmericanoService.initialize(eq(42L), any()))
                .thenThrow(new InvalidStateException("Se necesitan al menos 2 parejas confirmadas. Actuales: 1"));

        mockMvc.perform(post("/tournaments/team-americano/initialize")
                        .param("tournamentId", "42")
                        .with(user("admin@test").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/admin/tournaments/42"))
                .andExpect(flash().attribute("errorMessage", startsWith("Error al inicializar: ")));
    }

    @Test
    void finish_exito_redirigeAAdminTeamAmericano() throws Exception {
        when(teamAmericanoService.finishTournament(42L)).thenReturn(
                TeamAmericanoRankingDto.builder().tournamentId(42L).ranking(List.of()).build());

        mockMvc.perform(post("/tournaments/team-americano/admin/42/finish")
                        .with(user("admin@test").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/tournaments/team-americano/admin/42"))
                .andExpect(flash().attribute("success", "Torneo finalizado. Campeón: —"));
    }

    @Test
    void finish_error_redirigeAAdminTeamAmericano() throws Exception {
        when(teamAmericanoService.finishTournament(42L))
                .thenThrow(new InvalidStateException("No se puede finalizar. Quedan 1 rondas sin completar."));

        mockMvc.perform(post("/tournaments/team-americano/admin/42/finish")
                        .with(user("admin@test").roles("SUPER_ADMIN"))
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/tournaments/team-americano/admin/42"))
                .andExpect(flash().attribute("error", startsWith("Error al finalizar: ")));
    }
}
