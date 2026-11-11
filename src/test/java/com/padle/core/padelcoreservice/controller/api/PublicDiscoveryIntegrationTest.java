package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.model.enums.TournamentVisibility;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.padle.core.padelcoreservice.service.PublicDiscoveryService;
import org.springframework.dao.DataAccessResourceFailureException;
import static org.mockito.Mockito.doThrow;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
    "spring.mail.username=test@example.com", "spring.mail.password=test-password",
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
    "recaptcha.site-key=test-site-key", "recaptcha.secret-key=test-secret-key",
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
class PublicDiscoveryIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoSpyBean PublicDiscoveryService discovery;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRepository tournaments;
    @Autowired EntityManagerFactory entityManagerFactory;
    private Long clubId;
    private Long publicId;
    private Long hiddenId;

    @BeforeEach
    void fixtures() {
        clubId = clubs.saveAndFlush(Club.builder().nombre("Discovery " + UUID.randomUUID())
                .telefonoContacto("private-phone").emailContacto("private@example.test").build()).getId();
        publicId = event("public", TournamentVisibility.PUBLICO, TournamentStatus.REGISTRO_ABIERTO, true, Modalidad.INDIVIDUAL).getId();
        event("pair", TournamentVisibility.PUBLICO, TournamentStatus.CERRADO, true, Modalidad.DOBLES);
        hiddenId = event("hidden", TournamentVisibility.SOLO_POR_ENLACE, TournamentStatus.REGISTRO_ABIERTO, true, Modalidad.INDIVIDUAL).getId();
        event("inactive", TournamentVisibility.PUBLICO, TournamentStatus.REGISTRO_ABIERTO, false, Modalidad.INDIVIDUAL);
        event("draft", TournamentVisibility.PUBLICO, TournamentStatus.BORRADOR, true, Modalidad.INDIVIDUAL);
    }

    @Test
    void catalogFiltersBeforePaginationAndPreservesCapacityUnits() throws Exception {
        mvc.perform(get("/api/public/catalog").param("clubId", clubId.toString()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0]").exists()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items[0].id").value(publicId))
                .andExpect(jsonPath("$.items[0].capacityUnit").value("PLAYERS"))
                .andExpect(jsonPath("$.items[0].ownerId").doesNotExist());
        mvc.perform(get("/api/public/catalog").param("clubId", clubId.toString()).param("modality", "DOBLES").param("level", "C7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].capacityUnit").value("PAIRS"))
                .andExpect(jsonPath("$.items[0].registrationOpen").value(false));
        mvc.perform(get("/api/public/catalog").param("clubId", clubId.toString()).param("page", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0]").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void validationAndUnknownClubUseLocalizedProblemDetail() throws Exception {
        for (String[] filter : new String[][]{{"page", "-1"}, {"size", "51"}, {"type", "unknown"}, {"level", "unknown"}, {"from", "bad-date"}}) {
            mvc.perform(get("/api/public/catalog").param(filter[0], filter[1]).header("Accept-Language", "ru"))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("INVALID_CATALOG_FILTER"))
                    .andExpect(jsonPath("$.detail").value("Некорректные параметры каталога"));
        }
        mvc.perform(get("/api/public/catalog").param("from", "2030-02-01").param("to", "2030-01-01"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/clubs/9223372036854775807"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CLUB_NOT_FOUND"));
    }

    @Test
    void offsetBeyondJpaLimitReturnsValidationProblem() throws Exception {
        for (String[] pagination : new String[][]{{"2147483647", "12"}, {"178956971", "12"}, {"42949673", "50"}}) {
            mvc.perform(get("/api/public/catalog").param("clubId", clubId.toString())
                            .param("page", pagination[0]).param("size", pagination[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("INVALID_CATALOG_FILTER"));
        }
    }

    @Test
    void supportedLargeOffsetsPreserveEmptyPageAndTotals() throws Exception {
        for (String[] pagination : new String[][]{{"2147483647", "1"}, {"178956970", "12"}, {"42949672", "50"}}) {
            mvc.perform(get("/api/public/catalog").param("clubId", clubId.toString())
                            .param("page", pagination[0]).param("size", pagination[1]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0]").doesNotExist())
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(pagination[1].equals("1") ? 2 : 1));
        }
    }

    @Test
    void publicClubDoesNotExposeContactsOrAdministration() throws Exception {
        mvc.perform(get("/api/public/clubs/" + clubId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(clubId)).andExpect(jsonPath("$.createdBy").doesNotExist())
                .andExpect(jsonPath("$.telefonoContacto").doesNotExist()).andExpect(jsonPath("$.emailContacto").doesNotExist());
        var club = clubs.findById(clubId).orElseThrow();
        club.setIsActive(false); clubs.saveAndFlush(club);
        mvc.perform(get("/api/public/clubs/" + clubId)).andExpect(status().isNotFound());
    }

    @Test
    void directHiddenLinkRemainsReadableAndHomeExcludesHiddenDraftAndClosed() throws Exception {
        mvc.perform(get("/api/public/tournaments/" + hiddenId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(hiddenId));
        var response = mvc.perform(get("/api/public/home")).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClubs").exists()).andExpect(jsonPath("$.totalPlayers").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("private@example.test", "private-phone", "BORRADOR", "CERRADO");
    }

    @Test
    void listUsesBoundedQueriesForLargerPage() throws Exception {
        for (int index = 0; index < 5; index++) {
            event("more" + index, TournamentVisibility.PUBLICO, TournamentStatus.PUBLICADO, true, Modalidad.INDIVIDUAL);
        }
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long before = statistics.getPrepareStatementCount();
        mvc.perform(get("/api/public/catalog").param("clubId", clubId.toString()).param("size", "2"))
                .andExpect(status().isOk());
        long small = statistics.getPrepareStatementCount() - before;
        before = statistics.getPrepareStatementCount();
        mvc.perform(get("/api/public/catalog").param("clubId", clubId.toString()).param("size", "5"))
                .andExpect(status().isOk());
        assertThat(statistics.getPrepareStatementCount() - before).isLessThanOrEqualTo(small);
    }

    @Test
    void storageFailureDoesNotExposeInternalDetails() throws Exception {
        doThrow(new DataAccessResourceFailureException("internal database failure")).when(discovery).clubs();
        var body = mvc.perform(get("/api/public/clubs").header("Accept-Language", "en"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("PUBLIC_DATA_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("internal database failure", "stackTrace");
    }

    private Tournament event(String name, TournamentVisibility visibility, TournamentStatus status, boolean active, Modalidad modality) {
        return tournaments.saveAndFlush(Tournament.builder().clubId(clubId).nombre(name)
                .fechaInicio(LocalDate.now().plusDays(3)).horaInicio(LocalTime.of(18, 0))
                .generoFormato(GenderFormat.MASCULINO).categoriaNivel(Nivel.C7).tipo(TournamentType.AMERICANO)
                .modalidad(modality).cupoMax(8).precio(BigDecimal.valueOf(10000)).duracion("2h")
                .contactoOrganizador("private-phone").estado(status).isActive(active).visibilidad(visibility).build());
    }
}
