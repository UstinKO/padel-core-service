package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Match;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.MatchType;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.MatchRepository;
import com.padle.core.padelcoreservice.repository.OwnerRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LFPT-376: изоляция доступа по клубу — TournamentAccessService является единственным местом,
 * где принимается решение "может ли этот Owner управлять этим турниром". Покрывает основную
 * логику (SUPER_ADMIN/ADMIN — все, CLUB_ADMIN — только свой клуб, OWNER/ORGANIZER — только свой
 * турнир) и по одному резолверу производных ID на формат турнира (bracket-матч, King of Court,
 * Americano/Team Americano/Team Playoff — общие сущности AmericanoMatch/AmericanoTeam).
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
class TournamentAccessServiceTest {

    @Autowired
    private TournamentAccessService tournamentAccessService;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private OwnerRepository ownerRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private MatchRepository matchRepository;
    @Autowired
    private TournamentKingOfCourtRepository kingRepository;
    @Autowired
    private AmericanoRoundRepository americanoRoundRepository;
    @Autowired
    private AmericanoMatchRepository americanoMatchRepository;
    @Autowired
    private AmericanoTeamRepository americanoTeamRepository;

    // ==================== assertCanManageTournament ====================

    @Test
    void superAdmin_puedeGestionarCualquierTorneo() {
        Club clubA = createClub();
        Tournament tournament = createTournament(clubA.getId(), null);
        Owner superAdmin = createOwner(OwnerRole.SUPER_ADMIN, null);

        assertThatCode(() -> tournamentAccessService.assertCanManageTournament(superAdmin, tournament.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void admin_puedeGestionarCualquierTorneo() {
        Club clubA = createClub();
        Tournament tournament = createTournament(clubA.getId(), null);
        Owner admin = createOwner(OwnerRole.ADMIN, null);

        assertThatCode(() -> tournamentAccessService.assertCanManageTournament(admin, tournament.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void clubAdmin_puedeGestionarTorneoDeSuPropioClub() {
        Club club = createClub();
        Tournament tournament = createTournament(club.getId(), null);
        Owner clubAdmin = createOwner(OwnerRole.CLUB_ADMIN, club.getId());

        assertThatCode(() -> tournamentAccessService.assertCanManageTournament(clubAdmin, tournament.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void clubAdmin_noPuedeGestionarTorneoDeOtroClub() {
        Club clubA = createClub();
        Club clubB = createClub();
        Tournament tournamentOfClubB = createTournament(clubB.getId(), null);
        Owner clubAdminA = createOwner(OwnerRole.CLUB_ADMIN, clubA.getId());

        assertThatThrownBy(() -> tournamentAccessService.assertCanManageTournament(clubAdminA, tournamentOfClubB.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void clubAdmin_sinClubId_noPuedeGestionarNingunTorneo() {
        Club club = createClub();
        Tournament tournament = createTournament(club.getId(), null);
        Owner clubAdminSinClub = createOwner(OwnerRole.CLUB_ADMIN, null);

        assertThatThrownBy(() -> tournamentAccessService.assertCanManageTournament(clubAdminSinClub, tournament.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void organizer_puedeGestionarSuPropioTorneo() {
        Club club = createClub();
        Owner organizer = createOwner(OwnerRole.ORGANIZER, null);
        Tournament tournament = createTournament(club.getId(), organizer.getId());

        assertThatCode(() -> tournamentAccessService.assertCanManageTournament(organizer, tournament.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void organizer_noPuedeGestionarTorneoDeOtroOrganizer() {
        Club club = createClub();
        Owner ownerOfTournament = createOwner(OwnerRole.ORGANIZER, null);
        Owner otherOrganizer = createOwner(OwnerRole.ORGANIZER, null);
        Tournament tournament = createTournament(club.getId(), ownerOfTournament.getId());

        assertThatThrownBy(() -> tournamentAccessService.assertCanManageTournament(otherOrganizer, tournament.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ==================== resolvers por ID derivado — un formato cada uno ====================

    @Test
    void assertCanManageMatch_bracket_respetaClubDelTorneo() {
        Club clubA = createClub();
        Club clubB = createClub();
        Tournament tournament = createTournament(clubA.getId(), null);
        Match match = matchRepository.save(Match.builder()
                .tournamentId(tournament.getId())
                .ronda(1)
                .partidoNumero(1)
                .tipo(MatchType.INDIVIDUAL)
                .build());

        Owner clubAdminA = createOwner(OwnerRole.CLUB_ADMIN, clubA.getId());
        Owner clubAdminB = createOwner(OwnerRole.CLUB_ADMIN, clubB.getId());

        assertThatCode(() -> tournamentAccessService.assertCanManageMatch(clubAdminA, match.getId()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> tournamentAccessService.assertCanManageMatch(clubAdminB, match.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assertCanManageKing_kingOfCourt_respetaClubDelTorneo() {
        Club clubA = createClub();
        Club clubB = createClub();
        Tournament tournament = createTournament(clubA.getId(), null);

        TournamentKingOfCourt newKing = new TournamentKingOfCourt();
        newKing.setTournament(tournament);
        newKing.setMaxCourts(2);
        newKing.setCalibrationRounds(1);
        newKing.setCurrentRound(1);
        newKing.setIsActive(true);
        newKing.setIsFinished(false);
        newKing.setStartedAt(LocalDateTime.now());
        TournamentKingOfCourt king = kingRepository.save(newKing);

        Owner clubAdminA = createOwner(OwnerRole.CLUB_ADMIN, clubA.getId());
        Owner clubAdminB = createOwner(OwnerRole.CLUB_ADMIN, clubB.getId());

        assertThatCode(() -> tournamentAccessService.assertCanManageKing(clubAdminA, king.getId()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> tournamentAccessService.assertCanManageKing(clubAdminB, king.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assertCanManageAmericanoMatch_americanoYTeamPlayoff_respetaClubDelTorneo() {
        Club clubA = createClub();
        Club clubB = createClub();
        Tournament tournament = createTournament(clubA.getId(), null);

        AmericanoRound round = americanoRoundRepository.save(AmericanoRound.builder()
                .tournament(tournament)
                .roundNumber(1)
                .status(AmericanoRoundStatus.PENDING)
                .pointsPerMatch(24)
                .isDoubles(false)
                .courts(1)
                .build());

        AmericanoMatch match = americanoMatchRepository.save(AmericanoMatch.builder()
                .round(round)
                .tournament(tournament)
                .matchNumber(1)
                .isDoubles(false)
                .status(AmericanoRoundStatus.PENDING)
                .build());

        Owner clubAdminA = createOwner(OwnerRole.CLUB_ADMIN, clubA.getId());
        Owner clubAdminB = createOwner(OwnerRole.CLUB_ADMIN, clubB.getId());

        assertThatCode(() -> tournamentAccessService.assertCanManageAmericanoMatch(clubAdminA, match.getId()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> tournamentAccessService.assertCanManageAmericanoMatch(clubAdminB, match.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assertCanManageAmericanoTeam_teamPlayoff_respetaClubDelTorneo() {
        Club clubA = createClub();
        Club clubB = createClub();
        Tournament tournament = createTournament(clubA.getId(), null);
        PlayerPadel player = createPlayer();

        AmericanoTeam team = americanoTeamRepository.save(AmericanoTeam.builder()
                .tournament(tournament)
                .player1(player)
                .teamNumber(1)
                .build());

        Owner clubAdminA = createOwner(OwnerRole.CLUB_ADMIN, clubA.getId());
        Owner clubAdminB = createOwner(OwnerRole.CLUB_ADMIN, clubB.getId());

        assertThatCode(() -> tournamentAccessService.assertCanManageAmericanoTeam(clubAdminA, team.getId()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> tournamentAccessService.assertCanManageAmericanoTeam(clubAdminB, team.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ==================== canManage (variante booleana, usada para flags de listados) ====================

    @Test
    void canManage_devuelveBooleanoSinLanzarExcepcion() {
        Club club = createClub();
        Owner clubAdmin = createOwner(OwnerRole.CLUB_ADMIN, club.getId());

        assertThat(tournamentAccessService.canManage(clubAdmin, null, club.getId())).isTrue();
        assertThat(tournamentAccessService.canManage(clubAdmin, null, club.getId() + 1)).isFalse();
    }

    // ==================== helpers ====================

    private Club createClub() {
        return clubRepository.save(Club.builder()
                .nombre("Club " + UUID.randomUUID())
                .isActive(true)
                .build());
    }

    private Owner createOwner(OwnerRole role, Long clubId) {
        String suffix = "LFPT376-" + UUID.randomUUID();
        return ownerRepository.save(Owner.builder()
                .email(suffix + "@example.com")
                .password("irrelevant-hash")
                .firstName("Test")
                .lastName("Owner")
                .role(role)
                .clubId(clubId)
                .isActive(true)
                .build());
    }

    private PlayerPadel createPlayer() {
        String suffix = "LFPT376-" + UUID.randomUUID();
        return playerRepository.save(PlayerPadel.builder()
                .nombre("Test")
                .apellido("Player")
                .email(suffix + "@example.com")
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }

    private Tournament createTournament(Long clubId, Long ownerId) {
        String suffix = "LFPT376-" + UUID.randomUUID();
        return tournamentRepository.save(Tournament.builder()
                .clubId(clubId)
                .ownerId(ownerId)
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
