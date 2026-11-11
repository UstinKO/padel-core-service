package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.Format;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.Phase;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.State;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.PublicEventStateService;
import com.padle.core.padelcoreservice.service.PublicDiscoveryService;
import com.padle.core.padelcoreservice.dto.PublicClubResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.base-url=https://public.example.test",
    "spring.mail.username=test@example.com", "spring.mail.password=test-password",
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
    "recaptcha.site-key=test-site-key", "recaptcha.secret-key=test-secret-key"
})
class PublicEventIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean TournamentService tournaments;
    @MockitoBean PublicEventStateService states;
    @MockitoBean PublicDiscoveryService clubs;

    @BeforeEach
    void setup() {
        var event = TournamentDto.builder().id(42L).nombre("Copa <script>alert(1)</script> \"A\"")
                .clubId(7L).clubNombre("Club").tipo(TournamentType.AMERICANO_TEAMS).modalidad(Modalidad.DOBLES)
                .estado(TournamentStatus.REGISTRO_ABIERTO).generoFormato(GenderFormat.MIXTO).categoriaNivel("C7")
                .fechaInicio(LocalDate.of(2026, 12, 20)).horaInicio(LocalTime.of(18, 0)).precio(BigDecimal.TEN)
                .moneda("ARS").cupoMax(8).inscritosActuales(4).waitlistCount(1)
                .contactoOrganizador("+54 911 1234-5678").ownerId(99L).paymentInstructions("private payment")
                .infoDetallada("Reglas <b>públicas</b>").build();
        when(tournaments.getActiveTournamentById(42L)).thenReturn(Optional.of(event));
        when(tournaments.getActiveRegistrationsByTournament(42L)).thenReturn(List.of());
        when(states.state(any())).thenReturn(new State(Format.TEAM_PAIRS, Phase.NOT_STARTED, ""));
    }

    @Test
    void anonymousWebContractIsSafeAndMobileContractIsUnchanged() throws Exception {
        mvc.perform(get("/api/public/events/42"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.format").value("TEAM_PAIRS"))
                .andExpect(jsonPath("$.phase").value("NOT_STARTED"))
                .andExpect(jsonPath("$.event.capacityUnit").value("PAIRS"))
                .andExpect(jsonPath("$.organizer.contact").value("+54 911 1234-5678"))
                .andExpect(jsonPath("$.organizer.whatsappUrl").value("https://wa.me/5491112345678"))
                .andExpect(jsonPath("$.canonicalUrl").value("https://public.example.test/torneo/42"))
                .andExpect(jsonPath("$.event.ownerId").doesNotExist())
                .andExpect(jsonPath("$.event.paymentInstructions").doesNotExist())
                .andExpect(jsonPath("$.event.youtubeLink").doesNotExist());
        mvc.perform(get("/api/public/tournaments/42"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.organizer").doesNotExist()).andExpect(jsonPath("$.event").doesNotExist());
    }

    @Test
    void malformedAndMissingIdsReturnProblemDetails() throws Exception {
        mvc.perform(get("/api/public/events/invalid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_ID"));
        mvc.perform(get("/api/public/events/999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void previewMetadataIsServerRenderedEscapedAndIndependentOfHost() throws Exception {
        var html = mvc.perform(get("/preview/torneo/42").header("Host", "attacker.example")
                        .with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("property=\"og:title\"", "https://public.example.test/torneo/42", "&lt;script&gt;", "spa-test-shell", "/web/assets/app.test.js")
                .doesNotContain("attacker.example", "<script>alert", "private payment", "paymentInstructions");
    }

    @Test
    void clubMetadataAndHeadPreserveCanonicalAndNoindex() throws Exception {
        when(clubs.club(7L)).thenReturn(new PublicClubResponse(7L, "Club <&>", "Street", "City", "Description", null, null, null, "https://public.example.test/clubs/7"));
        var html = mvc.perform(get("/preview/clubs/7").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("https://public.example.test/clubs/7", "Club &lt;&amp;&gt;");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("/preview/clubs/7")
                        .with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
                .andExpect(content().string(""));
    }

    @Test
    void draftRemainsUnavailableToTheNewPublicContract() throws Exception {
        var source = tournaments.getActiveTournamentById(42L).orElseThrow();
        source.setEstado(TournamentStatus.BORRADOR);
        mvc.perform(get("/api/public/events/42")).andExpect(status().isNotFound());
    }

    @Test
    void cancelledConductDoesNotOfferRegistrationOrLiveActions() throws Exception {
        when(states.state(any())).thenReturn(new State(Format.TEAM_PLAYOFF, Phase.CANCELLED, "/tournaments/team-playoff/42"));
        mvc.perform(get("/api/public/events/42")).andExpect(status().isOk())
                .andExpect(jsonPath("$.actions[0].type").value("CALENDAR"))
                .andExpect(jsonPath("$.actions[1]").doesNotExist());
    }

    @Test
    void previewErrorsAreRealHttpErrorsAndUnauthorizedAccessStaysBlocked() throws Exception {
        mvc.perform(get("/preview/torneo/42")).andExpect(status().isFound());
        mvc.perform(get("/preview/torneo/999").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isNotFound());
        when(tournaments.getActiveTournamentById(42L)).thenThrow(new DataAccessResourceFailureException("private database"));
        var html = mvc.perform(get("/preview/torneo/42").with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("private database");
        mvc.perform(get("/api/public/events/42")).andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
