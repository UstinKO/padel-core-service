package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
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
 * LFPT-0729 (регрессия, поведение этой задачей не менялось): турнир с cupoMax=8 и 9
 * зарегистрированными парами (8 CONFIRMED + 1 WAITLIST, как их оставляет
 * DoubleTournamentRegistrationService) должен импортировать ровно 8 команд — резервная пара
 * никогда не попадает в квалификацию.
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
class TeamPlayoffServiceReserveImportTest {

    private static final int CUPO_MAX = 8;

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
    void importFromRegistrations_novenaParejaEnReserva_soloImportaLasOchoConfirmadas() {
        Long tournamentId = createTournament();

        for (int i = 1; i <= CUPO_MAX; i++) {
            createPair(tournamentId, RegistrationStatus.CONFIRMED, i, null);
        }
        createPair(tournamentId, RegistrationStatus.WAITLIST, null, 1);

        int imported = playoffService.importFromRegistrations(tournamentId);

        assertThat(imported).isEqualTo(CUPO_MAX);
        assertThat(playoffService.getTeams(tournamentId)).hasSize(CUPO_MAX);
    }

    private void createPair(Long tournamentId, RegistrationStatus status, Integer position, Integer waitlistPosition) {
        Tournament tournament = tournamentRepository.findById(tournamentId).orElseThrow();
        PlayerPadel main = createPlayer(tournamentId);
        PlayerPadel partner = createPlayer(tournamentId);
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament).player(main).partner(partner)
                .status(status).position(position).waitlistPosition(waitlistPosition)
                .isActive(true).isDoubleRegistration(true).mainPlayerId(main.getId())
                .build());
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament).player(partner).partner(main)
                .status(status).position(position).waitlistPosition(waitlistPosition)
                .isActive(true).isDoubleRegistration(true).mainPlayerId(main.getId())
                .build());
    }

    private Long createTournament() {
        String suffix = "LFPT0729-" + UUID.randomUUID();
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
                .cupoMax(CUPO_MAX)
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
                .nombre("LFPT0729")
                .apellido("Player" + suffix)
                .email("lfpt0729-" + suffix + "@example.com")
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }
}
