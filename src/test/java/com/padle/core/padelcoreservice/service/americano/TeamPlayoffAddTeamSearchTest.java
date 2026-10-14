package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.PaymentManagementViewDto;
import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffAddTeamRequest;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.service.PaymentService;
import com.padle.core.padelcoreservice.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LFPT-0561: ручное добавление команды через поиск игрока вместо обязательного ID — покрывает
 * все 4 комбинации существующий/новый из клиентского запроса, дедуп по телефону, и видимость
 * команды на странице «Оплаты и посещаемость» ({@code PaymentService.getPaymentManagementData}
 * читает только {@code TournamentRegistration}, а не {@code AmericanoTeam} напрямую).
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
class TeamPlayoffAddTeamSearchTest {

    @Autowired
    private TeamPlayoffService playoffService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void addTeam_bothExisting_createsTeamAndRegistersBothPlayers() {
        Long tournamentId = createTournament();
        PlayerPadel p1 = createPlayer("Ana", "Garcia");
        PlayerPadel p2 = createPlayer("Luis", "Fernandez");

        TeamPlayoffAddTeamRequest req = new TeamPlayoffAddTeamRequest();
        req.setPlayer1Id(p1.getId());
        req.setPlayer2Id(p2.getId());
        req.setHasPaid(true);
        req.setAttended(true);

        AmericanoTeam team = playoffService.addTeam(tournamentId, req);

        assertThat(team.getPlayer1().getId()).isEqualTo(p1.getId());
        assertThat(team.getPlayer2().getId()).isEqualTo(p2.getId());
        assertThatBothPlayersAppearInPayments(tournamentId, p1.getId(), p2.getId());
    }

    @Test
    void addTeam_player1Existing_player2New_createsRealProfileForPlayer2() {
        Long tournamentId = createTournament();
        PlayerPadel p1 = createPlayer("Marta", "Lopez");
        String phone = uniquePhone();

        TeamPlayoffAddTeamRequest req = new TeamPlayoffAddTeamRequest();
        req.setPlayer1Id(p1.getId());
        req.setPlayer2Nombre("Carlos");
        req.setPlayer2Apellido("Nuevo");
        req.setPlayer2Telefono(phone);

        AmericanoTeam team = playoffService.addTeam(tournamentId, req);

        PlayerPadel createdPlayer2 = team.getPlayer2();
        assertThat(createdPlayer2).isNotNull();
        assertThat(createdPlayer2.getTelefono()).isEqualTo(phone);
        assertThat(createdPlayer2.hasValidContact()).isTrue();
        assertThatBothPlayersAppearInPayments(tournamentId, p1.getId(), createdPlayer2.getId());
    }

    @Test
    void addTeam_player1New_player2Existing_createsRealProfileForPlayer1() {
        Long tournamentId = createTournament();
        PlayerPadel p2 = createPlayer("Sofia", "Diaz");
        String phone = uniquePhone();

        TeamPlayoffAddTeamRequest req = new TeamPlayoffAddTeamRequest();
        req.setPlayer1Nombre("Diego");
        req.setPlayer1Apellido("Nuevo");
        req.setPlayer1Telefono(phone);
        req.setPlayer2Id(p2.getId());

        AmericanoTeam team = playoffService.addTeam(tournamentId, req);

        PlayerPadel createdPlayer1 = team.getPlayer1();
        assertThat(createdPlayer1.getTelefono()).isEqualTo(phone);
        assertThat(createdPlayer1.hasValidContact()).isTrue();
        assertThatBothPlayersAppearInPayments(tournamentId, createdPlayer1.getId(), p2.getId());
    }

    @Test
    void addTeam_bothNew_createsTwoRealProfiles() {
        Long tournamentId = createTournament();
        String phone1 = uniquePhone();
        String phone2 = uniquePhone();

        TeamPlayoffAddTeamRequest req = new TeamPlayoffAddTeamRequest();
        req.setPlayer1Nombre("Walk");
        req.setPlayer1Apellido("InUno");
        req.setPlayer1Telefono(phone1);
        req.setPlayer2Nombre("Walk");
        req.setPlayer2Apellido("InDos");
        req.setPlayer2Telefono(phone2);

        AmericanoTeam team = playoffService.addTeam(tournamentId, req);

        assertThat(team.getPlayer1().getTelefono()).isEqualTo(phone1);
        assertThat(team.getPlayer2().getTelefono()).isEqualTo(phone2);
        assertThat(team.getPlayer1().isGuestAccount()).isTrue();
        assertThat(team.getPlayer2().isGuestAccount()).isTrue();
        assertThatBothPlayersAppearInPayments(tournamentId, team.getPlayer1().getId(), team.getPlayer2().getId());
    }

    @Test
    void addTeam_newPlayerWithExistingPhone_reusesExistingProfile_doesNotCreateDuplicate() {
        Long tournamentId = createTournament();
        String phone = uniquePhone();
        PlayerPadel existing = createPlayerWithPhone("Existing", "Player", phone);
        long countBefore = playerRepository.count();

        PlayerPadel p1 = createPlayer("Captain", "Uno");
        TeamPlayoffAddTeamRequest req = new TeamPlayoffAddTeamRequest();
        req.setPlayer1Id(p1.getId());
        // Координатор вводит другие имя/фамилию, но телефон совпадает с уже существующим
        // активным игроком — должен использоваться его профиль, а не создаваться новый.
        req.setPlayer2Nombre("Otro");
        req.setPlayer2Apellido("Nombre");
        req.setPlayer2Telefono(phone);

        AmericanoTeam team = playoffService.addTeam(tournamentId, req);

        assertThat(team.getPlayer2().getId()).isEqualTo(existing.getId());
        assertThat(playerRepository.count()).isEqualTo(countBefore + 1); // создался только p1
    }

    @Test
    void addTeam_samePersonBothSlots_throws() {
        Long tournamentId = createTournament();
        PlayerPadel p1 = createPlayer("Solo", "Persona");

        TeamPlayoffAddTeamRequest req = new TeamPlayoffAddTeamRequest();
        req.setPlayer1Id(p1.getId());
        req.setPlayer2Id(p1.getId());

        assertThatThrownBy(() -> playoffService.addTeam(tournamentId, req))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void addTeam_newPlayerMissingRequiredField_throws() {
        Long tournamentId = createTournament();
        PlayerPadel p1 = createPlayer("Capitan", "Equipo");

        TeamPlayoffAddTeamRequest req = new TeamPlayoffAddTeamRequest();
        req.setPlayer1Id(p1.getId());
        req.setPlayer2Nombre("SinTelefono");
        req.setPlayer2Apellido("Apellido");
        // sin player2Telefono

        assertThatThrownBy(() -> playoffService.addTeam(tournamentId, req))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void searchActivePlayers_findsByNameAndByPhone() {
        String suffix = UUID.randomUUID().toString();
        String phone = uniquePhone();
        PlayerPadel player = createPlayerWithPhone("Buscable" + suffix, "Apellido" + suffix, phone);

        List<PlayerResponseDto> byName = playerService.searchActivePlayers("Buscable" + suffix);
        assertThat(byName).extracting(PlayerResponseDto::getId).contains(player.getId());

        List<PlayerResponseDto> byPhone = playerService.searchActivePlayers(phone);
        assertThat(byPhone).extracting(PlayerResponseDto::getId).contains(player.getId());
    }

    private void assertThatBothPlayersAppearInPayments(Long tournamentId, Long player1Id, Long player2Id) {
        List<PaymentManagementViewDto> rows = paymentService.getPaymentManagementData(tournamentId);
        assertThat(rows).extracting(PaymentManagementViewDto::getPlayerId)
                .contains(player1Id, player2Id);
    }

    private Long createTournament() {
        String suffix = "LFPT561-" + UUID.randomUUID();
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
                .cupoMax(8)
                .precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("test@example.com")
                .isActive(true)
                .build());
        return tournament.getId();
    }

    private PlayerPadel createPlayer(String nombre, String apellido) {
        String suffix = UUID.randomUUID().toString();
        return playerRepository.save(PlayerPadel.builder()
                .nombre(nombre)
                .apellido(apellido)
                .email("lfpt561-" + suffix + "@example.com")
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }

    private PlayerPadel createPlayerWithPhone(String nombre, String apellido, String telefono) {
        String suffix = UUID.randomUUID().toString();
        return playerRepository.save(PlayerPadel.builder()
                .nombre(nombre)
                .apellido(apellido)
                .email("lfpt561-" + suffix + "@example.com")
                .telefono(telefono)
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }

    private String uniquePhone() {
        return "+5491" + System.nanoTime() % 100000000L;
    }
}
