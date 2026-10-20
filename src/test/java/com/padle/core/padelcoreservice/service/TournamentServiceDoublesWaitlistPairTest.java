package com.padle.core.padelcoreservice.service;

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
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-0668: processWaitlistForTournament debe confirmar las DOS filas de una pareja DOBLES
 * juntas (no una por una) — con un solo slot libre, antes de este fix quedaba un jugador
 * CONFIRMED y su compañero en WAITLIST (pareja rota).
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
class TournamentServiceDoublesWaitlistPairTest {

    @Autowired
    private TournamentService tournamentService;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private TournamentRegistrationRepository registrationRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void unSoloSlotLibre_confirmaLaPrimeraParejaCompleta_segundaQuedaEnteraEnReserva() {
        Tournament tournament = createDoublesTournament(1); // cupoMax=1 → solo 1 pareja puede entrar

        PlayerPadel p1a = createPlayer();
        PlayerPadel p1b = createPlayer();
        PlayerPadel p2a = createPlayer();
        PlayerPadel p2b = createPlayer();

        createWaitlistPair(tournament, p1a, p1b, 1);
        createWaitlistPair(tournament, p2a, p2b, 2);

        tournamentService.processWaitlistForTournament(tournament.getId());

        TournamentRegistration reg1a = registrationRepository.findByTournamentIdAndPlayerId(tournament.getId(), p1a.getId()).orElseThrow();
        TournamentRegistration reg1b = registrationRepository.findByTournamentIdAndPlayerId(tournament.getId(), p1b.getId()).orElseThrow();
        TournamentRegistration reg2a = registrationRepository.findByTournamentIdAndPlayerId(tournament.getId(), p2a.getId()).orElseThrow();
        TournamentRegistration reg2b = registrationRepository.findByTournamentIdAndPlayerId(tournament.getId(), p2b.getId()).orElseThrow();

        // Primera pareja (posición 1): AMBAS filas confirmadas — no solo una de las dos.
        assertThat(reg1a.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(reg1b.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);

        // Segunda pareja: sigue entera en reserva, ninguno de los dos quedó confirmado solo.
        assertThat(reg2a.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(reg2b.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
    }

    private void createWaitlistPair(Tournament tournament, PlayerPadel playerA, PlayerPadel playerB, int waitlistPosition) {
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament).player(playerA).partner(playerB)
                .status(RegistrationStatus.WAITLIST).waitlistPosition(waitlistPosition)
                .isActive(true).isDoubleRegistration(true).build());
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament).player(playerB).partner(playerA)
                .status(RegistrationStatus.WAITLIST).waitlistPosition(waitlistPosition)
                .isActive(true).isDoubleRegistration(true).build());
    }

    private PlayerPadel createPlayer() {
        String suffix = "LFPT0668-" + UUID.randomUUID();
        return playerRepository.save(PlayerPadel.builder()
                .nombre("Test").apellido("Player").email(suffix + "@example.com")
                .passwordHash("$2b$12$test").activo(true).emailConfirmado(true)
                .oauth2User(false).preferredLocale("es").nivelJugador(Nivel.C9)
                .build());
    }

    private Tournament createDoublesTournament(int cupoMax) {
        Club club = clubRepository.save(Club.builder()
                .nombre("Club " + UUID.randomUUID()).direccion("Av. Test 123").isActive(true).build());
        String suffix = "LFPT0668-" + UUID.randomUUID();
        return tournamentRepository.save(Tournament.builder()
                .clubId(club.getId())
                .nombre("Torneo " + suffix)
                .fechaInicio(LocalDateTime.now().plusDays(1).toLocalDate())
                .horaInicio(LocalDateTime.now().plusDays(1).toLocalTime())
                .generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7)
                .tipo(TournamentType.AMERICANO_TEAMS)
                .modalidad(Modalidad.DOBLES)
                .cupoMax(cupoMax)
                .precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("test@example.com")
                .isActive(true)
                .build());
    }
}
