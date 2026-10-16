package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.OwnerRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * LFPT-0546: главная админ-панель (GET /admin) для CLUB_ADMIN должна показывать статистику
 * («Активные турниры», «Лист ожидания», «Последние турниры») только своего клуба, а не всей
 * платформы; для остальных ролей поведение не должно измениться (регресс).
 * LFPT-0566: модель {@code GET /admin} также содержит флаг {@code isClubAdmin} — нужен шаблону
 * для скрытия карточки «Администраторы» для клубной учётной записи (#547).
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
class AdminPanelClubStatsTest {

    @Autowired
    private AdminController adminController;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private OwnerRepository ownerRepository;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private TournamentRegistrationRepository registrationRepository;
    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void adminPanel_clubAdminSinTorneosActivos_muestraCero_aunqueOtroClubTengaActivos() {
        Club clubPropio = createClub();
        Club clubAjeno = createClub();
        createTournament(clubAjeno.getId(), true);

        Owner clubAdmin = createClubAdmin(clubPropio.getId());

        Model model = new ExtendedModelMap();
        adminController.adminPanel(model, clubAdmin);

        assertThat(model.getAttribute("totalTournaments")).isEqualTo(0L);
        assertThat(model.getAttribute("totalWaitlist")).isEqualTo(0L);
        assertThat((List<?>) model.getAttribute("recentTournaments")).isEmpty();
    }

    @Test
    void adminPanel_clubAdmin_cuentaSoloTorneosActivosDeSuClub_noLosDeOtroClub() {
        Club clubPropio = createClub();
        Club clubAjeno = createClub();
        createTournament(clubPropio.getId(), true);
        createTournament(clubPropio.getId(), true);
        createTournament(clubAjeno.getId(), true);

        Owner clubAdmin = createClubAdmin(clubPropio.getId());

        Model model = new ExtendedModelMap();
        adminController.adminPanel(model, clubAdmin);

        assertThat(model.getAttribute("totalTournaments")).isEqualTo(2L);
    }

    @Test
    void adminPanel_clubAdmin_listaDeEsperaSoloDeSuClub() {
        Club clubPropio = createClub();
        Club clubAjeno = createClub();
        Tournament tournamentPropio = createTournament(clubPropio.getId(), true);
        Tournament tournamentAjeno = createTournament(clubAjeno.getId(), true);
        createWaitlistRegistration(tournamentPropio);
        createWaitlistRegistration(tournamentAjeno);
        createWaitlistRegistration(tournamentAjeno);

        Owner clubAdmin = createClubAdmin(clubPropio.getId());

        Model model = new ExtendedModelMap();
        adminController.adminPanel(model, clubAdmin);

        assertThat(model.getAttribute("totalWaitlist")).isEqualTo(1L);
    }

    @Test
    void adminPanel_clubAdmin_ultimosTorneosSoloDeSuClub() {
        Club clubPropio = createClub();
        Club clubAjeno = createClub();
        Tournament propio = createTournament(clubPropio.getId(), true);
        createTournament(clubAjeno.getId(), true);

        Owner clubAdmin = createClubAdmin(clubPropio.getId());

        Model model = new ExtendedModelMap();
        adminController.adminPanel(model, clubAdmin);

        @SuppressWarnings("unchecked")
        List<TournamentDto> recent = (List<TournamentDto>) model.getAttribute("recentTournaments");
        assertThat(recent).extracting(TournamentDto::getId).containsExactly(propio.getId());
    }

    @Test
    void adminPanel_clubAdminSinClubId_noFalla_devuelveCeros() {
        Club clubAjeno = createClub();
        createTournament(clubAjeno.getId(), true);

        Owner clubAdminSinClub = createClubAdmin(null);

        Model model = new ExtendedModelMap();
        assertThatCode(() -> adminController.adminPanel(model, clubAdminSinClub)).doesNotThrowAnyException();

        assertThat(model.getAttribute("totalTournaments")).isEqualTo(0L);
        assertThat(model.getAttribute("totalWaitlist")).isEqualTo(0L);
        assertThat((List<?>) model.getAttribute("recentTournaments")).isEmpty();
    }

    @Test
    void adminPanel_superAdmin_sigueViendoEstadisticaDeTodaLaPlataforma_regresion() {
        Club clubA = createClub();
        Club clubB = createClub();
        createTournament(clubA.getId(), true);
        createTournament(clubB.getId(), true);

        Owner superAdmin = createSuperAdmin();
        long totalActivosAntes = tournamentRepository.countByIsActiveTrue();

        Model model = new ExtendedModelMap();
        adminController.adminPanel(model, superAdmin);

        assertThat(model.getAttribute("totalTournaments")).isEqualTo(totalActivosAntes);
    }

    /**
     * LFPT-0566: flag {@code isClubAdmin} в модели {@code GET /admin}, нужен шаблону для скрытия
     * карточки «Администраторы» для клубной учётной записи (#547).
     */
    @Test
    void adminPanel_clubAdmin_modeloContieneIsClubAdminTrue() {
        Owner clubAdmin = createClubAdmin(createClub().getId());

        Model model = new ExtendedModelMap();
        adminController.adminPanel(model, clubAdmin);

        assertThat(model.getAttribute("isClubAdmin")).isEqualTo(true);
    }

    @Test
    void adminPanel_superAdmin_organizer_admin_modeloContieneIsClubAdminFalse() {
        Model modelSuperAdmin = new ExtendedModelMap();
        adminController.adminPanel(modelSuperAdmin, createSuperAdmin());
        assertThat(modelSuperAdmin.getAttribute("isClubAdmin")).isEqualTo(false);

        Model modelOrganizer = new ExtendedModelMap();
        adminController.adminPanel(modelOrganizer, createOwnerWithRole(OwnerRole.ORGANIZER));
        assertThat(modelOrganizer.getAttribute("isClubAdmin")).isEqualTo(false);

        Model modelAdmin = new ExtendedModelMap();
        adminController.adminPanel(modelAdmin, createOwnerWithRole(OwnerRole.ADMIN));
        assertThat(modelAdmin.getAttribute("isClubAdmin")).isEqualTo(false);
    }

    @Test
    void adminPanel_clubAdmin_sinClubId_isClubAdminSigueSiendoTrue() {
        Owner clubAdminSinClub = createClubAdmin(null);

        Model model = new ExtendedModelMap();
        adminController.adminPanel(model, clubAdminSinClub);

        assertThat(model.getAttribute("isClubAdmin")).isEqualTo(true);
    }

    @Test
    void adminPanel_superAdmin_otrosAtributosDelModeloNoCambian_regresion() {
        Owner superAdmin = createSuperAdmin();

        Model model = new ExtendedModelMap();
        adminController.adminPanel(model, superAdmin);

        assertThat(model.getAttribute("adminName")).isNotNull();
        assertThat(model.getAttribute("totalPlayers")).isNotNull();
        assertThat(model.getAttribute("totalOwners")).isNotNull();
        assertThat(model.getAttribute("totalTournaments")).isNotNull();
        assertThat(model.getAttribute("totalWaitlist")).isNotNull();
        assertThat(model.getAttribute("recentTournaments")).isNotNull();
        assertThat(model.getAttribute("recentTournamentsWithFlags")).isNotNull();
        assertThat(model.getAttribute("recentPlayers")).isNotNull();
        assertThat(model.getAttribute("matchesInProgress")).isNotNull();
        assertThat(model.getAttribute("upcomingMatches")).isNotNull();
        assertThat(model.getAttribute("activeTournaments")).isNotNull();
        assertThat(model.getAttribute("isSuperAdmin")).isEqualTo(true);
        assertThat(model.getAttribute("isAdminRole")).isEqualTo(false);
    }

    private Owner createClubAdmin(Long clubId) {
        String suffix = "LFPT0546-" + UUID.randomUUID();
        return ownerRepository.save(Owner.builder()
                .email(suffix + "@example.com")
                .password("irrelevant-hash")
                .firstName("Test")
                .lastName("ClubAdmin")
                .role(OwnerRole.CLUB_ADMIN)
                .clubId(clubId)
                .isActive(true)
                .build());
    }

    private Owner createSuperAdmin() {
        return createOwnerWithRole(OwnerRole.SUPER_ADMIN);
    }

    private Owner createOwnerWithRole(OwnerRole role) {
        String suffix = "LFPT0566-" + UUID.randomUUID();
        return ownerRepository.save(Owner.builder()
                .email(suffix + "@example.com")
                .password("irrelevant-hash")
                .firstName("Test")
                .lastName(role.name())
                .role(role)
                .isActive(true)
                .build());
    }

    private Club createClub() {
        return clubRepository.save(Club.builder().nombre("Club " + UUID.randomUUID()).isActive(true).build());
    }

    private Tournament createTournament(Long clubId, boolean isActive) {
        String suffix = "LFPT0546-" + UUID.randomUUID();
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
                .isActive(isActive)
                .build());
    }

    private void createWaitlistRegistration(Tournament tournament) {
        String suffix = "LFPT0546-" + UUID.randomUUID();
        PlayerPadel player = playerRepository.save(PlayerPadel.builder()
                .nombre("Test")
                .apellido("Waitlist")
                .email(suffix + "@example.com")
                .passwordHash("irrelevant-hash")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C7)
                .build());

        registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament)
                .player(player)
                .status(RegistrationStatus.WAITLIST)
                .isActive(true)
                .build());
    }
}
