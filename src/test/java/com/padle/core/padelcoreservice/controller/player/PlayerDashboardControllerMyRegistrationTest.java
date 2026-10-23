package com.padle.core.padelcoreservice.controller.player;

import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.service.TournamentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LFPT-0706: {@code GET /players/tournaments/{id}/my-registration} — campos nuevos
 * (registrationId/status/waitlistPosition/registrationDate/isDoubleRegistration), necesarios
 * para la inscripción en pareja desde 1Padel.App (LFPT-0707). Cadena real de Spring Security,
 * servicio simulado.
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
class PlayerDashboardControllerMyRegistrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TournamentService tournamentService;

    private static PlayerPadel player() {
        return PlayerPadel.builder()
                .id(42L)
                .nombre("Test")
                .apellido("Player")
                .email("my-registration@example.com")
                .passwordHash("$2a$10$fake")
                .activo(true)
                .emailConfirmado(true)
                .build();
    }

    @Test
    void parejaConfirmada_devuelveEstadoYDatosDePareja() throws Exception {
        LocalDateTime registrationDate = LocalDateTime.of(2026, 10, 20, 12, 0);
        TournamentRegistrationDto dto = new TournamentRegistrationDto();
        dto.setId(501L);
        dto.setStatus(RegistrationStatus.CONFIRMED);
        dto.setWaitlistPosition(null);
        dto.setRegistrationDate(registrationDate);
        dto.setIsDoubleRegistration(true);
        dto.setPartnerId(99L);
        dto.setPartnerNombre("Ana");
        dto.setPartnerApellido("Gomez");
        dto.setPartnerPhone("+5491111111111");

        when(tournamentService.getRegistration(7L, 42L)).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/players/tournaments/7/my-registration").with(user(player())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(true))
                .andExpect(jsonPath("$.registrationId").value(501))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.waitlistPosition").doesNotExist())
                .andExpect(jsonPath("$.registrationDate").exists())
                .andExpect(jsonPath("$.isDoubleRegistration").value(true))
                .andExpect(jsonPath("$.partnerId").value(99))
                .andExpect(jsonPath("$.partnerFirstName").value("Ana"))
                .andExpect(jsonPath("$.partnerLastName").value("Gomez"))
                .andExpect(jsonPath("$.hasRegisteredPartner").value(true))
                // Los contactos de la pareja nunca se publican por este endpoint.
                .andExpect(jsonPath("$.partnerPhone").doesNotExist())
                .andExpect(jsonPath("$.partnerTelegram").doesNotExist());
    }

    @Test
    void soloEnListaDeEspera_devuelvePosicionSinPareja() throws Exception {
        TournamentRegistrationDto dto = new TournamentRegistrationDto();
        dto.setId(502L);
        dto.setStatus(RegistrationStatus.WAITLIST);
        dto.setWaitlistPosition(3);
        dto.setRegistrationDate(LocalDateTime.now());
        dto.setIsDoubleRegistration(false);

        when(tournamentService.getRegistration(7L, 42L)).thenReturn(Optional.of(dto));

        mockMvc.perform(get("/players/tournaments/7/my-registration").with(user(player())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITLIST"))
                .andExpect(jsonPath("$.waitlistPosition").value(3))
                .andExpect(jsonPath("$.isDoubleRegistration").value(false))
                .andExpect(jsonPath("$.hasRegisteredPartner").value(false));
    }

    @Test
    void sinRegistroActivo_devuelveSoloRegisteredFalse() throws Exception {
        when(tournamentService.getRegistration(7L, 42L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/players/tournaments/7/my-registration").with(user(player())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registered").value(false))
                .andExpect(jsonPath("$.registrationId").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
    }
}
