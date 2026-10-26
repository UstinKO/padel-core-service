package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Клубный аккаунт (ROLE_CLUB_ADMIN) не выбирает статус турнира при создании —
 * сервер принудительно ставит REGISTRO_ABIERTO, независимо от значения из формы. Для
 * SUPER_ADMIN/ORGANIZER статус берётся из формы.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key",
})
@Transactional
class AdminTournamentCreateStatusTest {

    @Autowired
    private AdminController adminController;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private TournamentRepository tournamentRepository;

    @Test
    void createTournament_clubAdmin_estadoDelFormularioIgnorado_siempreRegistroAbierto() {
        Owner clubAdmin = createClubAdmin(createClub().getId());
        TournamentDto dto = buildDto();
        dto.setEstado(TournamentStatus.BORRADOR);
        dto.setInfoDetallada("Condiciones propias del club");

        Tournament created = submitAndFetch(dto, clubAdmin);

        assertThat(created.getEstado()).isEqualTo(TournamentStatus.REGISTRO_ABIERTO);
        assertThat(created.getInfoDetallada()).isEqualTo("Condiciones propias del club");
    }

    @Test
    void createTournament_clubAdmin_sinEstadoEnFormulario_tambienRegistroAbierto() {
        Owner clubAdmin = createClubAdmin(createClub().getId());
        TournamentDto dto = buildDto();
        dto.setEstado(null);

        Tournament created = submitAndFetch(dto, clubAdmin);

        assertThat(created.getEstado()).isEqualTo(TournamentStatus.REGISTRO_ABIERTO);
    }

    @Test
    void createTournament_superAdmin_respetaEstadoElegidoEnFormulario_sinRegresion() {
        Owner superAdmin = Owner.builder()
                .email("LFPT0558-super-" + UUID.randomUUID() + "@example.com")
                .password("irrelevant-hash")
                .firstName("Test")
                .lastName("SuperAdmin")
                .role(OwnerRole.SUPER_ADMIN)
                .isActive(true)
                .build();
        TournamentDto dto = buildDto();
        dto.setClubId(createClub().getId());
        dto.setEstado(TournamentStatus.BORRADOR);

        Tournament created = submitAndFetch(dto, superAdmin);

        assertThat(created.getEstado()).isEqualTo(TournamentStatus.BORRADOR);
    }

    @Test
    void createTournament_organizer_respetaEstadoElegidoEnFormulario_sinRegresion() {
        Owner organizer = Owner.builder()
                .email("LFPT0558-organizer-" + UUID.randomUUID() + "@example.com")
                .password("irrelevant-hash")
                .firstName("Test")
                .lastName("Organizer")
                .role(OwnerRole.ORGANIZER)
                .isActive(true)
                .build();
        TournamentDto dto = buildDto();
        dto.setClubId(createClub().getId());
        dto.setEstado(TournamentStatus.PUBLICADO);

        Tournament created = submitAndFetch(dto, organizer);

        assertThat(created.getEstado()).isEqualTo(TournamentStatus.PUBLICADO);
    }

    private Tournament submitAndFetch(TournamentDto dto, Owner owner) {
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();
        Model model = new ExtendedModelMap();
        String view = adminController.createTournament(
                dto, new BeanPropertyBindingResult(dto, "tournament"), owner, redirectAttributes, model);

        assertThat(view).as("создание не должно упасть в ветку ошибки").startsWith("redirect:/admin/tournaments/");
        Long id = Long.valueOf(view.substring(view.lastIndexOf('/') + 1));
        return tournamentRepository.findById(id).orElseThrow();
    }

    private Club createClub() {
        return clubRepository.save(Club.builder().nombre("Club LFPT0558-" + UUID.randomUUID()).isActive(true).build());
    }

    private Owner createClubAdmin(Long clubId) {
        return Owner.builder()
                .email("LFPT0558-club-" + UUID.randomUUID() + "@example.com")
                .password("irrelevant-hash")
                .firstName("Test")
                .lastName("ClubAdmin")
                .role(OwnerRole.CLUB_ADMIN)
                .clubId(clubId)
                .isActive(true)
                .build();
    }

    private TournamentDto buildDto() {
        return TournamentDto.builder()
                .fechaInicio(LocalDate.now().plusDays(7))
                .horaInicio(LocalTime.of(10, 0))
                .generoFormato(GenderFormat.MASCULINO)
                .categoriaNivel(Nivel.C6.name())
                .tipo(TournamentType.AMERICANO)
                .modalidad(Modalidad.INDIVIDUAL)
                .cupoMax(16)
                .precio(BigDecimal.TEN)
                .moneda("ARS")
                .contactoOrganizador("test@example.com")
                .build();
    }
}
