package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.ClubDto;
import com.padle.core.padelcoreservice.dto.MatchDto;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.controller.view.PaymentManagementController;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.OwnerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LFPT-376: изоляция доступа по клубу — проверяет, что реальные контроллеры (не только
 * TournamentAccessService напрямую) действительно вызывают проверку владения на потоках,
 * где её раньше не было вообще (турнир/регистрация, оплаты, bracket-матч), и что CLUB_ADMIN
 * своего клуба по-прежнему может выполнять те же действия (регресс).
 *
 * LFPT-393: дополнительно проверяет, что CLUB_ADMIN не может создавать новые клубы
 * (AdminClubController.newClubForm/createClub) — SUPER_ADMIN-only регресс.
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
class ClubAccessIsolationControllerTest {

    @Autowired
    private AdminController adminController;
    @Autowired
    private PaymentManagementController paymentManagementController;
    @Autowired
    private AdminMatchController adminMatchController;
    @Autowired
    private AdminClubController adminClubController;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private OwnerRepository ownerRepository;

    @Test
    void moveToWaitlist_clubAdminDeOtroClub_esRechazado_ySuPropioClubFunciona() {
        Club clubA = createClub();
        Club clubB = createClub();
        Long tournamentId = createTournament(clubA.getId()).getId();

        Owner clubAdminA = createOwner(clubA.getId());
        Owner clubAdminB = createOwner(clubB.getId());

        // Эндпоинт исторически (ещё до LFPT-376, для аналогичной проверки по ownerId) гасит
        // отказ доступа внутри себя и отвечает 200 с success:false — не 200 с чужими данными,
        // JSON-контракт для AJAX-вызова с фронтенда не меняется. Проверяем именно это, а не 403.
        ResponseEntity<Map<String, Object>> foreignResponse =
                adminController.moveToWaitlist(tournamentId, 999_999L, clubAdminB);
        assertThat(foreignResponse.getBody().get("success")).isEqualTo(false);

        // Регресс: свой клуб по-прежнему проходит проверку доступа (тот же ответ по форме —
        // success:false, но по другой причине: несуществующий playerId, не отказ в доступе).
        ResponseEntity<Map<String, Object>> ownResponse =
                adminController.moveToWaitlist(tournamentId, 999_999L, clubAdminA);
        assertThat(ownResponse.getBody().get("success")).isEqualTo(false);
        assertThat(ownResponse.getBody().get("message"))
                .as("клубный админ своего клуба должен получить бизнес-ошибку (нет такого игрока), а не отказ в доступе")
                .isNotEqualTo(foreignResponse.getBody().get("message"));
    }

    @Test
    void paymentManagementPage_clubAdminDeOtroClub_esRechazado_ySuPropioClubFunciona() {
        Club clubA = createClub();
        Club clubB = createClub();
        Long tournamentId = createTournament(clubA.getId()).getId();

        Owner clubAdminA = createOwner(clubA.getId());
        Owner clubAdminB = createOwner(clubB.getId());

        Model foreignModel = new ExtendedModelMap();
        assertThatThrownBy(() -> paymentManagementController.paymentManagementPage(tournamentId, foreignModel, clubAdminB))
                .isInstanceOf(AccessDeniedException.class);

        Model ownModel = new ExtendedModelMap();
        assertThatCode(() -> paymentManagementController.paymentManagementPage(tournamentId, ownModel, clubAdminA))
                .doesNotThrowAnyException();
        assertThat(ownModel.getAttribute("tournament")).isNotNull();
    }

    @Test
    void updateMatchResult_bracket_clubAdminDeOtroClub_esRechazado_ySuPropioClubFunciona() {
        Club clubA = createClub();
        Club clubB = createClub();
        Long tournamentId = createTournament(clubA.getId()).getId();

        Owner clubAdminA = createOwner(clubA.getId());
        Owner clubAdminB = createOwner(clubB.getId());

        MatchDto matchDto = new MatchDto();
        RedirectAttributes foreignRa = new RedirectAttributesModelMap();
        assertThatThrownBy(() -> adminMatchController.updateMatchResult(
                tournamentId, 999_999L, matchDto, clubAdminB, foreignRa))
                .isInstanceOf(AccessDeniedException.class);

        // Регресс: свой клуб проходит проверку доступа (падает дальше — matchId не существует,
        // контроллер это гасит внутри try/catch и делает redirect с ошибкой, не 403).
        RedirectAttributes ownRa = new RedirectAttributesModelMap();
        String view = adminMatchController.updateMatchResult(tournamentId, 999_999L, matchDto, clubAdminA, ownRa);
        assertThat(view).isEqualTo("redirect:/admin/tournaments/" + tournamentId + "/matches");
        assertThat(ownRa.getFlashAttributes().get("errorMessage")).isNotNull();
    }

    @Test
    void newClubForm_clubAdmin_esRechazado_ySuperAdminFunciona() {
        Club club = createClub();
        Owner clubAdmin = createOwner(club.getId());
        Owner superAdmin = createSuperAdmin();

        Model clubAdminModel = new ExtendedModelMap();
        RedirectAttributes clubAdminRa = new RedirectAttributesModelMap();
        String clubAdminView = adminClubController.newClubForm(clubAdminModel, clubAdmin, clubAdminRa);
        assertThat(clubAdminView).isEqualTo("redirect:/admin/clubs");
        assertThat(clubAdminRa.getFlashAttributes().get("errorMessage")).isNotNull();

        Model superAdminModel = new ExtendedModelMap();
        RedirectAttributes superAdminRa = new RedirectAttributesModelMap();
        String superAdminView = adminClubController.newClubForm(superAdminModel, superAdmin, superAdminRa);
        assertThat(superAdminView).isEqualTo("admin/clubs/form");
    }

    @Test
    void createClub_clubAdmin_esRechazado_noCreaClub_ySuperAdminFunciona() {
        Club club = createClub();
        Owner clubAdmin = createOwner(club.getId());
        Owner superAdmin = createSuperAdmin();
        long clubCountBefore = clubRepository.count();

        ClubDto clubAdminAttempt = ClubDto.builder().nombre("LFPT393-Hacker Club " + UUID.randomUUID()).build();
        RedirectAttributes clubAdminRa = new RedirectAttributesModelMap();
        String clubAdminView = adminClubController.createClub(
                clubAdminAttempt, new BeanPropertyBindingResult(clubAdminAttempt, "club"), clubAdmin, clubAdminRa);
        assertThat(clubAdminView).isEqualTo("redirect:/admin/clubs");
        assertThat(clubAdminRa.getFlashAttributes().get("errorMessage")).isNotNull();
        assertThat(clubRepository.count())
                .as("CLUB_ADMIN не должен создавать клубы")
                .isEqualTo(clubCountBefore);

        ClubDto superAdminAttempt = ClubDto.builder().nombre("LFPT393-Real Club " + UUID.randomUUID()).build();
        RedirectAttributes superAdminRa = new RedirectAttributesModelMap();
        String superAdminView = adminClubController.createClub(
                superAdminAttempt, new BeanPropertyBindingResult(superAdminAttempt, "club"), superAdmin, superAdminRa);
        assertThat(superAdminView).startsWith("redirect:/admin/clubs/");
        assertThat(clubRepository.count())
                .as("SUPER_ADMIN по-прежнему может создавать клубы (регресс)")
                .isEqualTo(clubCountBefore + 1);
    }

    private Owner createSuperAdmin() {
        String suffix = "LFPT393-" + UUID.randomUUID();
        return ownerRepository.save(Owner.builder()
                .email(suffix + "@example.com")
                .password("irrelevant-hash")
                .firstName("Test")
                .lastName("SuperAdmin")
                .role(OwnerRole.SUPER_ADMIN)
                .isActive(true)
                .build());
    }

    private Club createClub() {
        return clubRepository.save(Club.builder().nombre("Club " + UUID.randomUUID()).isActive(true).build());
    }

    private Owner createOwner(Long clubId) {
        String suffix = "LFPT376-" + UUID.randomUUID();
        return ownerRepository.save(Owner.builder()
                .email(suffix + "@example.com")
                .password("irrelevant-hash")
                .firstName("Test")
                .lastName("Owner")
                .role(OwnerRole.CLUB_ADMIN)
                .clubId(clubId)
                .isActive(true)
                .build());
    }

    private Tournament createTournament(Long clubId) {
        String suffix = "LFPT376-" + UUID.randomUUID();
        return tournamentRepository.save(Tournament.builder()
                .clubId(clubId)
                .nombre("Torneo " + suffix)
                .fechaInicio(LocalDate.now().plusDays(7))
                .horaInicio(LocalTime.of(10, 0))
                .generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7)
                .tipo(TournamentType.AMERICANO)
                .modalidad(Modalidad.INDIVIDUAL)
                .cupoMax(8)
                .precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("test@example.com")
                .isActive(true)
                .build());
    }
}
