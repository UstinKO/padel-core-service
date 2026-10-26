package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffAddTeamRequest;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.AmericanoPlayerStatus;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.RegistrationSource;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
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
 * Команда с гостевым (незарегистрированным на сайте) вторым игроком должна отображаться
 * в матчах Team Playoff так же полно, как команда с обоими зарегистрированными игроками —
 * второй игрок не должен пропадать из DTO только потому, что у него нет аккаунта.
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
class TeamPlayoffGuestPartnerDisplayTest {

    @Autowired
    private TeamPlayoffService playoffService;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private AmericanoTeamRepository teamRepository;

    @Test
    void toMatchDto_showsGuestPartnerName_whenSecondPlayerHasNoAccount() {
        Long tournamentId = createTournament();

        // playoffService.addTeam всегда ищет/создаёт настоящий PlayerPadel для обоих игроков,
        // а AmericanoTeam с текстовым player2 (без аккаунта) встречается в реальных данных
        // (например, команда, импортированная из парной регистрации с сайта, чей партнёр так и
        // не подтвердил аккаунт — см. TeamPlayoffService.buildTeamFromPair). Поэтому команда
        // строится напрямую, минуя addTeam, чтобы покрыть логику отображения, которую проверяет
        // этот тест.
        PlayerPadel registeredCaptain = createPlayer(tournamentId, "Registered", "Captain");
        Tournament tournament = tournamentRepository.findById(tournamentId).orElseThrow();
        AmericanoTeam guestTeam = teamRepository.save(AmericanoTeam.builder()
                .tournament(tournament)
                .player1(registeredCaptain)
                .player2Name("Invitado SinCuenta")
                .player2Phone("+5491100000000")
                .teamNumber(1)
                .currentPosition(0)
                .status(AmericanoPlayerStatus.ACTIVE)
                .registrationSource(RegistrationSource.ADMIN_MANUAL)
                .hasPaid(true)
                .attended(true)
                .build());

        PlayerPadel opponent1 = createPlayer(tournamentId, "Opponent", "One");
        PlayerPadel opponent2 = createPlayer(tournamentId, "Opponent", "Two");
        TeamPlayoffAddTeamRequest fullTeamReq = new TeamPlayoffAddTeamRequest();
        fullTeamReq.setPlayer1Id(opponent1.getId());
        fullTeamReq.setPlayer2Id(opponent2.getId());
        fullTeamReq.setRegistrationSource("ADMIN_MANUAL");
        fullTeamReq.setHasPaid(true);
        fullTeamReq.setAttended(true);
        AmericanoTeam fullTeam = playoffService.addTeam(tournamentId, fullTeamReq);

        AmericanoMatch match = playoffService.createQualificationMatch(
                tournamentId, guestTeam.getId(), fullTeam.getId(), 1);

        AmericanoMatchDto dto = playoffService.toMatchDto(match);

        assertThat(dto.getTeam1Player1Name()).isEqualTo("Registered Captain");
        assertThat(dto.getTeam1Player2Name())
                .as("гостевой партнёр должен быть виден, даже без аккаунта на сайте")
                .isEqualTo("Invitado SinCuenta");
        assertThat(dto.getTeam1Player2Id()).isNull();

        assertThat(dto.getTeam2Player1Name()).isEqualTo("Opponent One");
        assertThat(dto.getTeam2Player2Name()).isEqualTo("Opponent Two");
        assertThat(dto.getTeam2Player2Id()).isEqualTo(opponent2.getId());
    }

    private Long createTournament() {
        String suffix = "LFPT360-" + UUID.randomUUID();

        Club club = clubRepository.save(Club.builder()
                .nombre("Club " + suffix)
                .isActive(true)
                .build());

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

    private PlayerPadel createPlayer(Long tournamentId, String nombre, String apellido) {
        String emailSuffix = tournamentId + "-" + UUID.randomUUID();
        return playerRepository.save(PlayerPadel.builder()
                .nombre(nombre)
                .apellido(apellido)
                .email("lfpt360-" + emailSuffix + "@example.com")
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }
}
