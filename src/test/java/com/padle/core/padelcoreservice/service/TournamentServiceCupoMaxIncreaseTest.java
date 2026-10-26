package com.padle.core.padelcoreservice.service;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-0729: увеличение cupoMax турнира DOBLES освобождает места — самая старая полная пара в
 * резерве (WAITLIST) должна автоматически перейти в CONFIRMED, без ручного вмешательства
 * координатора.
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
class TournamentServiceCupoMaxIncreaseTest {

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
    @Autowired
    private OwnerRepository ownerRepository;

    @Test
    void aumentarCupoMax_parejaCompletaEnReserva_pasaAConfirmedAutomaticamente() {
        Tournament tournament = createDoublesTournament(1); // cupoMax=1 → уже есть 1 подтверждённая пара
        Owner admin = createSuperAdmin();

        PlayerPadel confirmedA = createPlayer();
        PlayerPadel confirmedB = createPlayer();
        createConfirmedPair(tournament, confirmedA, confirmedB, 1);

        PlayerPadel reserveA = createPlayer();
        PlayerPadel reserveB = createPlayer();
        createWaitlistPair(tournament, reserveA, reserveB, 1);

        TournamentDto updateDto = tournamentService.getTournamentDtoById(tournament.getId()).orElseThrow();
        updateDto.setCupoMax(2);

        tournamentService.updateTournament(tournament.getId(), updateDto, admin);

        TournamentRegistration regA = registrationRepository
                .findByTournamentIdAndPlayerId(tournament.getId(), reserveA.getId()).orElseThrow();
        TournamentRegistration regB = registrationRepository
                .findByTournamentIdAndPlayerId(tournament.getId(), reserveB.getId()).orElseThrow();

        assertThat(regA.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(regB.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void cupoMaxSinCambios_noTocaElResto() {
        Tournament tournament = createDoublesTournament(1);
        Owner admin = createSuperAdmin();

        PlayerPadel reserveA = createPlayer();
        PlayerPadel reserveB = createPlayer();
        createWaitlistPair(tournament, reserveA, reserveB, 1);

        TournamentDto updateDto = tournamentService.getTournamentDtoById(tournament.getId()).orElseThrow();
        // cupoMax не изменился — новых мест нет, промоушена быть не должно.

        tournamentService.updateTournament(tournament.getId(), updateDto, admin);

        TournamentRegistration regA = registrationRepository
                .findByTournamentIdAndPlayerId(tournament.getId(), reserveA.getId()).orElseThrow();
        assertThat(regA.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
    }

    private void createConfirmedPair(Tournament tournament, PlayerPadel playerA, PlayerPadel playerB, int position) {
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament).player(playerA).partner(playerB)
                .status(RegistrationStatus.CONFIRMED).position(position)
                .isActive(true).isDoubleRegistration(true).build());
        registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament).player(playerB).partner(playerA)
                .status(RegistrationStatus.CONFIRMED).position(position)
                .isActive(true).isDoubleRegistration(true).build());
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
        String suffix = "LFPT0729-" + UUID.randomUUID();
        return playerRepository.save(PlayerPadel.builder()
                .nombre("Test").apellido("Player").email(suffix + "@example.com")
                .passwordHash("$2b$12$test").activo(true).emailConfirmado(true)
                .oauth2User(false).preferredLocale("es").nivelJugador(Nivel.C9)
                .build());
    }

    private Owner createSuperAdmin() {
        String suffix = "LFPT0729-" + UUID.randomUUID();
        return ownerRepository.save(Owner.builder()
                .email(suffix + "@example.com").password("$2b$12$test")
                .firstName("QA").lastName("Admin").role(OwnerRole.SUPER_ADMIN).isActive(true)
                .build());
    }

    private Tournament createDoublesTournament(int cupoMax) {
        Club club = clubRepository.save(Club.builder()
                .nombre("Club " + UUID.randomUUID()).direccion("Av. Test 123").isActive(true).build());
        String suffix = "LFPT0729-" + UUID.randomUUID();
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
