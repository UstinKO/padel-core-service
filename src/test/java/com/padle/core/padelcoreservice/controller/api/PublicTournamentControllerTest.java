package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.model.enums.TournamentVisibility;
import com.padle.core.padelcoreservice.service.TournamentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LFPT-0623: публичное расписание анонимно доступно через реальную цепочку
 * Spring Security, не раскрывает поля управления/контакты и не расширяет
 * авторизацию существующих защищённых маршрутов.
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
class PublicTournamentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TournamentService tournamentService;

    @BeforeEach
    void schedule() {
        when(tournamentService.getActiveTournamentsForHome()).thenReturn(List.of(fixture()));
        when(tournamentService.getActiveTournamentById(42L)).thenReturn(Optional.of(fixture()));
        when(tournamentService.getActiveTournamentById(999L)).thenReturn(Optional.empty());
    }

    @Test
    void anonimo_veListaConDatosPublicosSinCamposInternos() throws Exception {
        mockMvc.perform(get("/api/public/tournaments").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].id").value(42))
                .andExpect(jsonPath("$[0].club.name").value("Club de prueba"))
                .andExpect(jsonPath("$[0].capacityUnit").value("PLAYERS"))
                .andExpect(jsonPath("$[0].available").value(3))
                .andExpect(jsonPath("$[0].type").value("AMERICANO"))
                .andExpect(jsonPath("$[0].currency").value("ARS"))
                .andExpect(jsonPath("$[0].price").value(40000))
                .andExpect(jsonPath("$[0].startsAt").value("2026-10-24T15:00:00-03:00"))
                .andExpect(jsonPath("$[0].ownerId").doesNotExist())
                .andExpect(jsonPath("$[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$[0].contactoOrganizador").doesNotExist())
                .andExpect(jsonPath("$[0].visibilidad").doesNotExist())
                .andExpect(jsonPath("$[0].registrations").doesNotExist());
    }

    @Test
    void listaVacia_noEsError() throws Exception {
        when(tournamentService.getActiveTournamentsForHome()).thenReturn(List.of());

        mockMvc.perform(get("/api/public/tournaments"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void detalle_incluyeTurneoSoloPorEnlaceComoHaceLaPaginaWeb() throws Exception {
        TournamentDto event = fixture();
        event.setVisibilidad(TournamentVisibility.SOLO_POR_ENLACE);
        when(tournamentService.getActiveTournamentById(42L)).thenReturn(Optional.of(event));

        mockMvc.perform(get("/api/public/tournaments/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));
    }

    @Test
    void detalle_idDesconocidoDa404ConCodigoMaquina() throws Exception {
        mockMvc.perform(get("/api/public/tournaments/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOURNAMENT_NOT_FOUND"));
    }

    @Test
    void detalle_idNoNumericoDa400ConCodigoMaquina() throws Exception {
        mockMvc.perform(get("/api/public/tournaments/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TOURNAMENT_ID"));
    }

    @Test
    void errorDeBaseDeDatos_noFiltraDetallesInternos() throws Exception {
        when(tournamentService.getActiveTournamentsForHome())
                .thenThrow(new DataAccessResourceFailureException("private-db-host:5432"));

        mockMvc.perform(get("/api/public/tournaments"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"code\":\"SCHEDULE_UNAVAILABLE\"}"));
    }

    @Test
    void rutasProtegidasYMutaciones_siguenRequiriendoAutenticacion() throws Exception {
        mockMvc.perform(get("/api/tournaments").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/api/public/tournaments").with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/api/tournaments").with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private TournamentDto fixture() {
        return TournamentDto.builder().id(42L).nombre("Evento de prueba")
                .clubId(1L).clubNombre("Club de prueba").clubDireccion("Dirección de prueba")
                .fechaInicio(LocalDate.of(2026, 10, 24)).horaInicio(LocalTime.of(15, 0))
                .modalidad(Modalidad.INDIVIDUAL).cupoMax(8).inscritosActuales(5)
                .disponibles(3).waitlistCount(0)
                .tipo(TournamentType.AMERICANO).generoFormato(GenderFormat.MASCULINO)
                .precio(new BigDecimal("40000")).moneda("ARS").duracion("3 horas")
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("organizador@example.com")
                .ownerId(900L).createdBy(900L).build();
    }
}
