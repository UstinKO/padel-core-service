package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffTeamRequest;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LFPT-357: un jugador no debe terminar simultáneamente en dos equipos activos del mismo torneo.
 * Antes de este fix, la comprobación de duplicados sólo miraba {@code player1Id} — si la misma
 * pareja física se reinscribía con los roles principal/compañero invertidos (p. ej. tras cancelar
 * y volver a registrarse), ambos jugadores podían terminar en dos equipos distintos a la vez.
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
class TeamPlayoffDuplicatePreventionTest {

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

    @Test
    void addTeamFromRegistration_rejectsPairReRegisteredWithSwappedRoles() {
        Long tournamentId = createTournament();
        PlayerPadel playerA = createPlayer(tournamentId);
        PlayerPadel playerB = createPlayer(tournamentId);

        TournamentRegistration firstAttempt = registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournamentRepository.findById(tournamentId).orElseThrow())
                .player(playerA)
                .partner(playerB)
                .status(RegistrationStatus.CONFIRMED)
                .position(1)
                .isDoubleRegistration(true)
                .mainPlayerId(playerA.getId())
                .build());
        playoffService.addTeamFromRegistration(tournamentId, firstAttempt.getId(), true, true);

        // Simula: la pareja canceló y volvió a registrarse, esta vez con B como jugador principal.
        TournamentRegistration secondAttempt = registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournamentRepository.findById(tournamentId).orElseThrow())
                .player(playerB)
                .partner(playerA)
                .status(RegistrationStatus.CONFIRMED)
                .position(2)
                .isDoubleRegistration(true)
                .mainPlayerId(playerB.getId())
                .build());

        assertThatThrownBy(() ->
                playoffService.addTeamFromRegistration(tournamentId, secondAttempt.getId(), true, true))
                .isInstanceOf(InvalidStateException.class);

        assertThat(playoffService.getTeams(tournamentId)).hasSize(1);
    }

    @Test
    void importFromRegistrations_skipsPairWithPartnerAlreadyOnAnotherTeam() {
        Long tournamentId = createTournament();
        PlayerPadel playerA = createPlayer(tournamentId);
        PlayerPadel playerB = createPlayer(tournamentId);
        PlayerPadel playerC = createPlayer(tournamentId);

        TeamPlayoffTeamRequest manualReq = new TeamPlayoffTeamRequest();
        manualReq.setPlayer1Id(playerB.getId());
        manualReq.setPlayer2Id(playerC.getId());
        playoffService.addTeam(tournamentId, manualReq);

        // A se registra con B, pero B ya está en el equipo manual de arriba (como player2).
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournamentRepository.findById(tournamentId).orElseThrow())
                .player(playerA)
                .partner(playerB)
                .status(RegistrationStatus.CONFIRMED)
                .position(1)
                .isDoubleRegistration(true)
                .mainPlayerId(playerA.getId())
                .build());

        int imported = playoffService.importFromRegistrations(tournamentId);

        assertThat(imported).isZero();
        assertThat(playoffService.getTeams(tournamentId)).hasSize(1);
    }

    @Test
    void addTeam_rejectsWhenPlayer2AlreadyOnAnotherTeam() {
        Long tournamentId = createTournament();
        PlayerPadel playerA = createPlayer(tournamentId);
        PlayerPadel playerB = createPlayer(tournamentId);
        PlayerPadel playerC = createPlayer(tournamentId);

        TeamPlayoffTeamRequest firstReq = new TeamPlayoffTeamRequest();
        firstReq.setPlayer1Id(playerA.getId());
        firstReq.setPlayer2Id(playerB.getId());
        playoffService.addTeam(tournamentId, firstReq);

        TeamPlayoffTeamRequest secondReq = new TeamPlayoffTeamRequest();
        secondReq.setPlayer1Id(playerC.getId());
        secondReq.setPlayer2Id(playerB.getId());

        assertThatThrownBy(() -> playoffService.addTeam(tournamentId, secondReq))
                .isInstanceOf(InvalidStateException.class);

        assertThat(playoffService.getTeams(tournamentId)).hasSize(1);
    }

    private Long createTournament() {
        String suffix = "LFPT357-" + UUID.randomUUID();
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

    private PlayerPadel createPlayer(Long tournamentId) {
        String suffix = tournamentId + "-" + UUID.randomUUID();
        return playerRepository.save(PlayerPadel.builder()
                .nombre("LFPT357")
                .apellido("Player" + suffix)
                .email("lfpt357-" + suffix + "@example.com")
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }
}
