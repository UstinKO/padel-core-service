package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-348/LFPT-357: номер команды ({@link AmericanoTeam#getTeamNumber()}), под которым пара
 * отображается на всех стадиях турнира, должен совпадать с позицией пары, реально показанной
 * на странице оплат ({@code PaymentService.getPaymentManagementData}) — это НЕ обязательно
 * сырое значение {@link TournamentRegistration#getPosition()} (страница оплат нормализует его,
 * устраняя дубли/пропуски — LFPT-357), и уж точно не порядок, в котором координатор нажимает
 * "Agregar equipo".
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
class TeamPlayoffTeamNumberingTest {

    @Autowired
    private TeamPlayoffService playoffService;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private TournamentRegistrationRepository registrationRepository;

    /**
     * Воспроизводит сценарий из фидбека организатора: пара с "Posición" 5 добавлена в турнир
     * раньше пар с "Posición" 1 и 2 — но т.к. в турнире всего 3 подтверждённые пары (позиции
     * 1, 2 и 5 — разрыв между 2 и 5 типичен после отменённых/повторных регистраций, LFPT-357),
     * реально показанная на странице оплат "Posición" для пары №5 — 3-я по счёту, не "5".
     */
    @Test
    void teamNumber_matchesRegistrationPosition_regardlessOfAddOrder() {
        Long tournamentId = createTournament();

        TournamentRegistration reg2 = createDoubleRegistration(tournamentId, 2);
        TournamentRegistration reg5 = createDoubleRegistration(tournamentId, 5);
        TournamentRegistration reg1 = createDoubleRegistration(tournamentId, 1);

        AmericanoTeam team5 = playoffService.addTeamFromRegistration(tournamentId, reg5.getId(), true, true);
        AmericanoTeam team1 = playoffService.addTeamFromRegistration(tournamentId, reg1.getId(), true, true);
        AmericanoTeam team2 = playoffService.addTeamFromRegistration(tournamentId, reg2.getId(), true, true);

        assertThat(team1.getTeamNumber()).isEqualTo(1);
        assertThat(team2.getTeamNumber()).isEqualTo(2);
        assertThat(team5.getTeamNumber()).isEqualTo(3);
    }

    /**
     * LFPT-357: dos parejas confirmadas con el MISMO {@code position} en bruto (p. ej. tras
     * cancelar y volver a registrarse sin que se reordenaran las posiciones) — la normalización
     * compartida con la página de pagos les asigna números distintos y consecutivos, no el mismo
     * número duplicado.
     */
    @Test
    void teamNumber_deduplicatesRawPositionCollisions() {
        Long tournamentId = createTournament();

        TournamentRegistration regA = createDoubleRegistration(tournamentId, 1);
        TournamentRegistration regB = createDoubleRegistration(tournamentId, 1);

        AmericanoTeam teamA = playoffService.addTeamFromRegistration(tournamentId, regA.getId(), true, true);
        AmericanoTeam teamB = playoffService.addTeamFromRegistration(tournamentId, regB.getId(), true, true);

        assertThat(teamA.getTeamNumber()).isNotEqualTo(teamB.getTeamNumber());
        assertThat(teamA.getTeamNumber()).isIn(1, 2);
        assertThat(teamB.getTeamNumber()).isIn(1, 2);
    }

    @Test
    void teamNumber_fallsBackToNextFreeNumber_whenRegistrationHasNoPosition() {
        Long tournamentId = createTournament();
        TournamentRegistration reg = createDoubleRegistration(tournamentId, null);

        AmericanoTeam team = playoffService.addTeamFromRegistration(tournamentId, reg.getId(), true, true);

        assertThat(team.getTeamNumber()).isEqualTo(1);
    }

    /**
     * LFPT-357: `addTeamFromRegistration` admite {@code PAIR_REGISTERED} (a diferencia de la
     * página de pagos, que sólo normaliza {@code CONFIRMED}/{@code PARTNER_INVITED}) — para esa
     * inscripción no hay posición normalizada, y el fallback al contador debe seguir aplicando
     * sin lanzar excepción, aunque tenga un `position` en bruto asignado.
     */
    @Test
    void teamNumber_fallsBackToNextFreeNumber_forPairRegisteredStatus() {
        Long tournamentId = createTournament();
        TournamentRegistration reg = registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournamentRepository.findById(tournamentId).orElseThrow())
                .player(createSoloPlayer(tournamentId))
                .status(RegistrationStatus.PAIR_REGISTERED)
                .position(7)
                .isDoubleRegistration(true)
                .mainPlayerId(null)
                .partnerFirstName("Partner")
                .partnerLastName("PairRegistered")
                .build());

        AmericanoTeam team = playoffService.addTeamFromRegistration(tournamentId, reg.getId(), true, true);

        assertThat(team.getTeamNumber()).isEqualTo(1);
    }

    /** Смешение путей создания команды: ручная команда уже заняла номер 3 — импорт из регистрации с той же позицией не должен на неё покуситься. */
    @Test
    void teamNumber_fallsBackToNextFreeNumber_whenPositionAlreadyTakenByAnotherTeam() {
        Long tournamentId = createTournament();

        com.padle.core.padelcoreservice.dto.americano.TeamPlayoffTeamRequest manualReq =
                new com.padle.core.padelcoreservice.dto.americano.TeamPlayoffTeamRequest();
        manualReq.setPlayer1Id(createSoloPlayer(tournamentId).getId());
        manualReq.setPlayer2Name("Manual Partner");
        AmericanoTeam manualTeam = playoffService.addTeam(tournamentId, manualReq);
        assertThat(manualTeam.getTeamNumber()).isEqualTo(1);

        TournamentRegistration reg = createDoubleRegistration(tournamentId, 1);
        AmericanoTeam team = playoffService.addTeamFromRegistration(tournamentId, reg.getId(), true, true);

        assertThat(team.getTeamNumber()).isEqualTo(2);
    }

    private Long createTournament() {
        String suffix = "LFPT348-" + UUID.randomUUID();
        Club club = clubRepository.save(Club.builder().nombre("Club " + suffix).isActive(true).build());
        Tournament tournament = tournamentRepository.save(Tournament.builder()
                .clubId(club.getId())
                .nombre("Torneo " + suffix)
                .fechaInicio(LocalDate.now().plusDays(7))
                .horaInicio(LocalTime.of(10, 0))
                .generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7)
                .tipo(TournamentType.AMERICANO_TEAMS)
                .modalidad(Modalidad.DOBLES)
                .cupoMax(16)
                .precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("test@example.com")
                .isActive(true)
                .build());
        return tournament.getId();
    }

    private PlayerPadel createSoloPlayer(Long tournamentId) {
        String suffix = tournamentId + "-" + UUID.randomUUID();
        return playerRepository.save(PlayerPadel.builder()
                .nombre("LFPT348")
                .apellido("Solo" + suffix)
                .email("lfpt348-solo-" + suffix + "@example.com")
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }

    private TournamentRegistration createDoubleRegistration(Long tournamentId, Integer position) {
        String suffix = tournamentId + "-" + UUID.randomUUID();
        PlayerPadel main = playerRepository.save(PlayerPadel.builder()
                .nombre("LFPT348")
                .apellido("Main" + suffix)
                .email("lfpt348-main-" + suffix + "@example.com")
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());

        Tournament tournament = tournamentRepository.findById(tournamentId).orElseThrow();
        return registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament)
                .player(main)
                .status(RegistrationStatus.CONFIRMED)
                .position(position)
                .isDoubleRegistration(true)
                .mainPlayerId(main.getId())
                .partnerFirstName("Partner")
                .partnerLastName(suffix)
                .build());
    }
}
