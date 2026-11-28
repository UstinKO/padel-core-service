package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.padelcoreservice.support.OwnerFixtures;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.exception.OrganizerContactException;
import com.padle.core.padelcoreservice.mapper.TournamentMapper;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.persistence.tournament.Tournament;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.application.tournament.Modalidad;
import com.padle.core.application.tournament.TournamentStatus;
import com.padle.core.application.tournament.TournamentType;
import com.padle.core.application.tournament.TournamentVisibility;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.persistence.tournament.TournamentRepository;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.padelcoreservice.service.TournamentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.mail.username=test@example.com", "spring.mail.password=test-password",
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
    "recaptcha.site-key=test-site-key", "recaptcha.secret-key=test-secret-key"
})
@Transactional
class OrganizerContactIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired OwnerFixtures owners;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRepository tournaments;
    @Autowired TournamentService service;
    @Autowired TournamentMapper mapper;
    @Autowired EntityManager em;
    private static final AtomicInteger IP = new AtomicInteger();
    private String ip;
    private Club club;
    private Owner actor;

    @BeforeEach
    void prepare() {
        ip = "198.19.0." + IP.incrementAndGet();
        club = clubs.save(Club.builder().nombre("Contact QA " + UUID.randomUUID()).isActive(true).build());
        actor = account(OwnerRole.CLUB_ADMIN);
    }

    @ParameterizedTest
    @EnumSource(TournamentType.class)
    void everyFormatNormalizesPersistsAndPreservesOriginalOnEdit(TournamentType type) {
        var input = dto(type);
        input.setOrganizerName("QA organizer");
        input.setOrganizerPhone("011 15-2345-6789");
        input.setOrganizerCountry("AR");
        input.setOrganizerContactOriginal("client must not overwrite provenance");
        Long id = service.createTournament(input, actor.getId()).getId();
        em.flush(); em.clear();
        Tournament saved = tournaments.findById(id).orElseThrow();
        assertThat(saved.getOrganizerPhone()).isEqualTo("+5491123456789");
        assertThat(saved.getContactoOrganizador()).isEqualTo(saved.getOrganizerPhone());
        assertThat(saved.getOrganizerName()).isEqualTo("QA organizer");
        assertThat(saved.getOrganizerContactOriginal()).isEqualTo("011 15-2345-6789");
        var edited = mapper.toDto(saved);
        edited.setOrganizerPhone("https://wa.me/34612345678?text=Hola");
        service.updateTournament(id, edited, actor).orElseThrow();
        em.flush(); em.clear();
        var reopened = service.getTournamentDtoById(id).orElseThrow();
        assertThat(reopened.getOrganizerPhone()).isEqualTo("+34612345678");
        assertThat(reopened.getOrganizerCountry()).isEqualTo("ES");
        assertThat(reopened.getOrganizerContact().whatsappUrl()).isEqualTo("https://wa.me/34612345678");
        assertThat(reopened.getOrganizerContactOriginal()).isEqualTo("011 15-2345-6789");
    }

    @Test
    void legacyIsResolvedWithoutWritingAndOriginalSurvivesCorrection() {
        Tournament old = mapper.toEntity(dto(TournamentType.AMERICANO));
        old.setOwnerId(actor.getId());
        old.setNombre("Legacy QA");
        old.setContactoOrganizador("QA organizer +54 (9) 11 2345-6789");
        old = tournaments.saveAndFlush(old);
        Long id = old.getId(); em.clear();
        var view = service.getTournamentDtoById(id).orElseThrow();
        assertThat(view.getOrganizerName()).isEqualTo("QA organizer");
        assertThat(view.getOrganizerContact().whatsappUrl()).isEqualTo("https://wa.me/5491123456789");
        assertThat(tournaments.findById(id).orElseThrow().getOrganizerPhone()).isNull();
        view.setOrganizerPhone("+34612345678");
        service.updateTournament(id, view, actor);
        em.flush(); em.clear();
        assertThat(tournaments.findById(id).orElseThrow().getOrganizerContactOriginal())
                .isEqualTo("QA organizer +54 (9) 11 2345-6789");
    }

    @Test
    void directServiceRejectsInvalidContactBeforeInsert() {
        var input = dto(TournamentType.AMERICANO);
        input.setOrganizerPhone("@contact");
        long before = tournaments.count();
        assertThatThrownBy(() -> service.createTournament(input, actor.getId()))
                .isInstanceOf(OrganizerContactException.class);
        assertThat(tournaments.count()).isEqualTo(before);
    }

    @Test
    void invalidFormPreservesFieldsAndReportsThePhoneField() throws Exception {
        mvc.perform(post("/admin/tournaments").with(user(actor)).with(csrf())
                .with(request -> { request.setRemoteAddr(ip); return request; })
                .param("organizerPhone", "Name 123").param("organizerCountry", "AR")
                .param("organizerName", "Preserved name").param("infoDetallada", "Keep this description")
                .param("clubId", club.getId().toString()).param("categoriaNivel", "C6")
                .param("fechaInicio", "2040-01-01").param("horaInicio", "14:00")
                .param("tipo", "AMERICANO").param("generoFormato", "MASCULINO")
                .param("modalidad", "DOBLES").param("cupoMax", "16").param("precio", "100"))
            .andExpect(status().isOk()).andExpect(view().name("admin/tournaments/form"))
            .andExpect(model().attributeHasFieldErrors("tournament", "organizerPhone"))
            .andExpect(model().attributeExists("organizerCountries"))
            .andExpect(result -> {
                var dto = (TournamentDto) result.getModelAndView().getModel().get("tournament");
                assertThat(dto.getInfoDetallada()).isEqualTo("Keep this description");
                assertThat(dto.getOrganizerName()).isEqualTo("Preserved name");
            });
    }

    @Test
    void invalidEditKeepsTheOriginalEventAsTheNextSubmitTarget() throws Exception {
        Long id = service.createTournament(dto(TournamentType.AMERICANO), actor.getId()).getId();
        mvc.perform(post("/admin/tournaments/" + id + "/edit").with(user(actor)).with(csrf())
                .param("id", "999999").param("organizerPhone", "not a phone"))
            .andExpect(status().isOk()).andExpect(view().name("admin/tournaments/form"))
            .andExpect(model().attributeHasFieldErrors("tournament", "organizerPhone"))
            .andExpect(result -> {
                var returned = (TournamentDto) result.getModelAndView().getModel().get("tournament");
                assertThat(returned.getId()).isEqualTo(id);
            });
        assertThat(tournaments.findById(id).orElseThrow().getOrganizerPhone()).isEqualTo("+5491123456789");
    }

    @Test
    void previewChecksRightsCsrfAndNeverChangesStoredContact() throws Exception {
        Long id = service.createTournament(dto(TournamentType.AMERICANO), actor.getId()).getId();
        String json = "{\"eventId\":" + id + ",\"phone\":\"+34612345678\",\"country\":\"AR\"}";
        for (OwnerRole role : new OwnerRole[]{OwnerRole.ORGANIZER, OwnerRole.CLUB_ADMIN}) {
            mvc.perform(post("/admin/api/organizer-contact/preview").header("Accept", "application/json").with(user(account(role))).with(csrf())
                    .contentType("application/json").content(json)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/api/organizer-contact/preview").header("Accept", "application/json").with(user(actor)).with(csrf())
                .contentType("application/json").content(json))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.contact.country").value("ES"))
            .andExpect(jsonPath("$.contact.whatsappUrl").value("https://wa.me/34612345678"));
        mvc.perform(post("/admin/api/organizer-contact/preview").header("Accept", "application/json").with(user(account(OwnerRole.SUPER_ADMIN))).with(csrf())
                .contentType("application/json").content(json)).andExpect(status().isOk());
        mvc.perform(post("/admin/api/organizer-contact/preview").header("Accept", "application/json").with(user(actor))
                .contentType("application/json").content(json)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/api/organizer-contact/countries").header("Accept", "application/json").with(user("player").roles("PLAYER")))
            .andExpect(status().isForbidden());
        assertThat(tournaments.findById(id).orElseThrow().getOrganizerPhone()).isEqualTo("+5491123456789");
    }

    @Test
    void previewErrorsAreLocalizedAndCopyRetainsContact() throws Exception {
        for (String lang : new String[]{"ru", "es", "en"}) {
            mvc.perform(post("/admin/api/organizer-contact/preview").header("Accept", "application/json").param("lang", lang)
                    .with(user(actor)).with(csrf()).contentType("application/json")
                    .content("{\"phone\":\"@handle\",\"country\":\"AR\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.contact.whatsappUrl").value(""));
        }
        Long id = service.createTournament(dto(TournamentType.AMERICANO), actor.getId()).getId();
        mvc.perform(get("/admin/tournaments/" + id + "/copy").with(user(actor)))
            .andExpect(status().isOk()).andExpect(model().attributeExists("organizerCountries"))
            .andExpect(result -> {
                var copied = (TournamentDto) result.getModelAndView().getModel().get("tournament");
                assertThat(copied.getId()).isNull();
                assertThat(copied.getOrganizerPhone()).isEqualTo("+5491123456789");
            });
    }

    private Owner account(OwnerRole role) {
        return owners.save(Owner.builder().email(UUID.randomUUID() + "@example.com").password("test-password")
                .firstName("Contact").lastName("QA").role(role)
                .clubIds(new HashSet<>(Set.of(club.getId()))).build());
    }
    private TournamentDto dto(TournamentType type) {
        return TournamentDto.builder().clubId(club.getId()).fechaInicio(LocalDate.of(2040,1,1))
                .horaInicio(LocalTime.of(14,0)).timeZone("America/Argentina/Buenos_Aires").generoFormato(GenderFormat.MASCULINO).categoriaNivel("C6")
                .tipo(type).modalidad(type == TournamentType.AMERICANO_TEAMS ? Modalidad.DOBLES : Modalidad.INDIVIDUAL)
                .cupoMax(16).moneda("ARS").precio(BigDecimal.ZERO).contactoOrganizador("+5491123456789")
                .estado(TournamentStatus.BORRADOR).mostrarNivel(false).visibilidad(TournamentVisibility.PUBLICO).build();
    }
}
