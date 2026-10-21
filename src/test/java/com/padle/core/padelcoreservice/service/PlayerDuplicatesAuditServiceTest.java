package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.DuplicateGroupDto;
import com.padle.core.padelcoreservice.dto.DuplicateMatchType;
import com.padle.core.padelcoreservice.dto.DuplicatesAuditReportDto;
import com.padle.core.padelcoreservice.dto.PlayerSummaryDto;
import com.padle.core.padelcoreservice.mapper.PlayerSummaryMapperImpl;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * LFPT-0677: группировка/нормализация/эвристики {@link PlayerDuplicatesAuditService} на
 * заранее подготовленном наборе игроков — сценарии из спеки
 * {@code specs/features/LFPT-0677-аудит-дублей-игроков.md} (критерии приёмки).
 */
@ExtendWith(MockitoExtension.class)
class PlayerDuplicatesAuditServiceTest {

    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private TournamentRegistrationRepository registrationRepository;

    private PlayerDuplicatesAuditService service;

    // Точный дубль по телефону (разный формат)
    private final PlayerPadel juanUno = player(1L, "Juan", "Perez", "juan1@test.com", "+5491122334455", null, false);
    private final PlayerPadel juanDos = player(2L, "Juan", "Perez", "juan2@test.com", "01122334455", null, false);
    // Точный дубль по Telegram (разный регистр)
    private final PlayerPadel pedroUno = player(3L, "Pedro", "Gomez", "pedro1@test.com", null, "PedroG", false);
    private final PlayerPadel pedroDos = player(4L, "Pedro", "Gomez", "pedro2@test.com", null, "pedrog", false);
    // Вероятный дубль по имени (разный регистр/пробелы), без совпадающих контактов
    private final PlayerPadel mariaUno = player(5L, "Maria", "Lopez", "maria1@test.com", null, null, false);
    private final PlayerPadel mariaDos = player(6L, " maria ", "LOPEZ", "maria2@test.com", null, null, false);
    // Гостевой аккаунт администратора
    private final PlayerPadel guest = player(7L, "Carlos", "Ruiz", "guest.123@1padel.guest", "5491100000000", null, false);
    // Google OAuth2
    private final PlayerPadel oauthPlayer = player(8L, "Ana", "Diaz", "ana@test.com", "5491100000001", null, true);
    // Создан через confirmPartnerRegistration (эвристика — по id в findPartnerInviteCreatedPlayerIds)
    private final PlayerPadel partnerCreated = player(9L, "Luis", "Gomez", "luis@test.com", "5491100000002", null, false);
    // Обычная самостоятельная регистрация, ни с кем не совпадает
    private final PlayerPadel selfRegistered = player(10L, "Sofia", "Martinez", "sofia@test.com", "5491100000003", null, false);

    private static PlayerPadel player(
            Long id, String nombre, String apellido, String email, String telefono,
            String telegramUsername, boolean oauth2User) {
        return PlayerPadel.builder()
                .id(id)
                .nombre(nombre)
                .apellido(apellido)
                .email(email)
                .telefono(telefono)
                .telegramUsername(telegramUsername)
                .oauth2User(oauth2User)
                .nivelJugador(Nivel.SIN_ESPECIFICAR)
                .activo(true)
                .build();
    }

    @BeforeEach
    void setUp() {
        service = new PlayerDuplicatesAuditService(playerRepository, registrationRepository, new PlayerSummaryMapperImpl());

        List<PlayerPadel> allPlayers = List.of(
                juanUno, juanDos, pedroUno, pedroDos, mariaUno, mariaDos, guest, oauthPlayer, partnerCreated, selfRegistered);
        when(playerRepository.findAll()).thenReturn(allPlayers);
        when(registrationRepository.findPartnerInviteCreatedPlayerIds()).thenReturn(List.of(partnerCreated.getId()));
    }

    @Test
    void totalRecords_cuentaTodasLasFilas() {
        DuplicatesAuditReportDto report = service.buildReport();

        assertThat(report.summary().totalRecords()).isEqualTo(10);
    }

    @Test
    void telefonosEnFormatoDistinto_sonDuplicadoExacto() {
        DuplicatesAuditReportDto report = service.buildReport();

        List<DuplicateGroupDto> phoneGroups = report.exactDuplicates().stream()
                .filter(g -> g.matchType() == DuplicateMatchType.PHONE)
                .toList();
        assertThat(phoneGroups).hasSize(1);
        assertThat(ids(phoneGroups.get(0))).containsExactlyInAnyOrder(juanUno.getId(), juanDos.getId());
    }

    @Test
    void telegramEnDistintoRegistro_esDuplicadoExacto() {
        DuplicatesAuditReportDto report = service.buildReport();

        List<DuplicateGroupDto> telegramGroups = report.exactDuplicates().stream()
                .filter(g -> g.matchType() == DuplicateMatchType.TELEGRAM)
                .toList();
        assertThat(telegramGroups).hasSize(1);
        assertThat(ids(telegramGroups.get(0))).containsExactlyInAnyOrder(pedroUno.getId(), pedroDos.getId());
    }

    @Test
    void mismoNombreSinContactosComunes_esDuplicadoProbable_yNoExacto() {
        DuplicatesAuditReportDto report = service.buildReport();

        assertThat(report.probableDuplicates()).hasSize(1);
        assertThat(ids(report.probableDuplicates().get(0))).containsExactlyInAnyOrder(mariaUno.getId(), mariaDos.getId());
        // Juan/Juan (mismo nombre) ya están cubiertos por el grupo exacto de teléfono —
        // no deben aparecer también como "probable".
        assertThat(report.probableDuplicates()).noneMatch(g -> ids(g).contains(juanUno.getId()));
    }

    @Test
    void jugadorCreadoPorFlujoDePartner_apareceEnPartnerFlowSuspects() {
        DuplicatesAuditReportDto report = service.buildReport();

        assertThat(report.partnerFlowSuspects())
                .extracting(PlayerSummaryDto::id)
                .containsExactly(partnerCreated.getId());
    }

    @Test
    void sourceBreakdown_clasificaCadaJugadorEnUnaSolaCategoria() {
        DuplicatesAuditReportDto report = service.buildReport();

        assertThat(report.sourceBreakdown())
                .containsEntry("ADMIN_GUEST", 1L)
                .containsEntry("OAUTH2", 1L)
                .containsEntry("PARTNER_INVITE", 1L)
                .containsEntry("SELF_REGISTERED", 7L);
    }

    @Test
    void distribucionYEstimacionDeUnicos_consideraComponentesConexos() {
        DuplicatesAuditReportDto report = service.buildReport();

        // 3 pares (Juan+Juan, Pedro+Pedro, Maria+Maria) => 3 grupos de tamaño 2, nada de 3+.
        assertThat(report.summary().duplicateGroupsCount()).isEqualTo(3);
        assertThat(report.summary().recordsPerPlayerDistribution()).containsEntry("2", 3L);
        assertThat(report.summary().uniquePlayersEstimate()).isEqualTo(7);
    }

    @Test
    void baseVacia_noFallaYDevuelveReporteVacio() {
        when(playerRepository.findAll()).thenReturn(List.of());
        when(registrationRepository.findPartnerInviteCreatedPlayerIds()).thenReturn(List.of());

        DuplicatesAuditReportDto report = service.buildReport();

        assertThat(report.summary().totalRecords()).isZero();
        assertThat(report.exactDuplicates()).isEmpty();
        assertThat(report.probableDuplicates()).isEmpty();
        assertThat(report.partnerFlowSuspects()).isEmpty();
    }

    private static Set<Long> ids(DuplicateGroupDto group) {
        return group.members().stream().map(PlayerSummaryDto::id).collect(Collectors.toSet());
    }
}
