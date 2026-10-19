package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.service.TournamentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LFPT-0637: {@code GET /api/players/me/tournaments} («Мои мероприятия» для клиента
 * 1Padel.App) — через реальную цепочку Spring Security, сервис замокан.
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
class PlayerContactControllerMyTournamentsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TournamentService tournamentService;

    @Test
    void jugadorAutenticado_recibeUpcomingEHistorial() throws Exception {
        PlayerPadel player = PlayerPadel.builder()
                .id(42L)
                .nombre("Test")
                .apellido("Player")
                .email("my-tournaments@example.com")
                .passwordHash("$2a$10$fake")
                .activo(true)
                .emailConfirmado(true)
                .build();

        TournamentDto upcomingTournament = TournamentDto.builder().id(1L).nombre("Torneo Abierto").build();
        TournamentDto historicalTournament = TournamentDto.builder().id(2L).nombre("Torneo Pasado").build();
        TournamentService.MyTournamentEntry entry = new TournamentService.MyTournamentEntry(
                upcomingTournament, RegistrationStatus.WAITLIST, 3, LocalDateTime.now());
        TournamentService.PlayerTournamentsOverview overview = new TournamentService.PlayerTournamentsOverview(
                List.of(entry), List.of(historicalTournament));

        when(tournamentService.getMyTournamentsOverview(42L)).thenReturn(overview);

        mockMvc.perform(get("/api/players/me/tournaments").with(user(player)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming[0].tournament.id").value(1))
                .andExpect(jsonPath("$.upcoming[0].registrationStatus").value("WAITLIST"))
                .andExpect(jsonPath("$.upcoming[0].waitlistPosition").value(3))
                .andExpect(jsonPath("$.historical[0].id").value(2));
    }

    @Test
    void owner_recibe403_sinTocarElServicio() throws Exception {
        Owner owner = Owner.builder().id(7L).email("admin@test").password("x")
                .firstName("Test").lastName("Owner").role(OwnerRole.SUPER_ADMIN).isActive(true).build();

        mockMvc.perform(get("/api/players/me/tournaments").with(user(owner)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(tournamentService);
    }

    @Test
    void anonimo_noAccedeAlEndpoint() throws Exception {
        mockMvc.perform(get("/api/players/me/tournaments"))
                .andExpect(status().is3xxRedirection());

        verifyNoInteractions(tournamentService);
    }
}
