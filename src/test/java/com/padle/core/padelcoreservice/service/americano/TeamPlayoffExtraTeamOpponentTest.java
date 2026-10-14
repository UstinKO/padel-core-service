package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchSuggestionDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffAddTeamRequest;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-550: при нечётном числе команд "дополнительная" команда (ещё не сыгравшая ни одного
 * квалификационного матча) должна рекомендоваться в соперники проигравшему первого завершившегося
 * матча (а не победителю) — проигравший получает свой 2-й квалификационный матч раньше.
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
class TeamPlayoffExtraTeamOpponentTest {

    @Autowired
    private TeamPlayoffService playoffService;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void suggestSecondRoundOpponent_recommendsExtraTeamToLoser_notWinner() {
        Long tournamentId = createTournament();
        AmericanoTeam winner = createTeam(tournamentId);
        AmericanoTeam loser = createTeam(tournamentId);
        AmericanoTeam busy1 = createTeam(tournamentId);
        AmericanoTeam busy2 = createTeam(tournamentId);
        AmericanoTeam extra = createTeam(tournamentId);

        AmericanoMatch firstMatch = playoffService.createQualificationMatch(
                tournamentId, winner.getId(), loser.getId(), 1);
        playoffService.createQualificationMatch(tournamentId, busy1.getId(), busy2.getId(), 2);
        playoffService.submitQualResult(firstMatch.getId(), 6, 3);

        Optional<AmericanoTeamDto> forLoser = playoffService.suggestSecondRoundOpponent(tournamentId, loser.getId());
        assertThat(forLoser).isPresent();
        assertThat(forLoser.get().getId()).isEqualTo(extra.getId());

        Optional<AmericanoTeamDto> forWinner = playoffService.suggestSecondRoundOpponent(tournamentId, winner.getId());
        assertThat(forWinner).isPresent();
        assertThat(forWinner.get().getId()).isNotEqualTo(extra.getId());
    }

    @Test
    void suggestSecondRoundPairings_pairsExtraTeamWithLoser_notWinner() {
        Long tournamentId = createTournament();
        AmericanoTeam winner = createTeam(tournamentId);
        AmericanoTeam loser = createTeam(tournamentId);
        AmericanoTeam busy1 = createTeam(tournamentId);
        AmericanoTeam busy2 = createTeam(tournamentId);
        AmericanoTeam extra = createTeam(tournamentId);

        AmericanoMatch firstMatch = playoffService.createQualificationMatch(
                tournamentId, winner.getId(), loser.getId(), 1);
        playoffService.createQualificationMatch(tournamentId, busy1.getId(), busy2.getId(), 2);
        playoffService.submitQualResult(firstMatch.getId(), 6, 3);

        List<AmericanoMatchSuggestionDto> suggestions = playoffService.suggestSecondRoundPairings(tournamentId);

        assertThat(suggestions).hasSize(1);
        AmericanoMatchSuggestionDto suggestion = suggestions.get(0);
        assertThat(suggestion.isPriority()).isTrue();
        assertThat(suggestion.getTeam1().getId()).isEqualTo(loser.getId());
        assertThat(suggestion.getTeam2().getId()).isEqualTo(extra.getId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SETUP
    // ═══════════════════════════════════════════════════════════════════════

    private Long createTournament() {
        String suffix = "LFPT550-" + UUID.randomUUID();
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
                .cupoMax(32)
                .precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("test@example.com")
                .isActive(true)
                .build());
        return tournament.getId();
    }

    private AmericanoTeam createTeam(Long tournamentId) {
        PlayerPadel p1 = createPlayer(tournamentId);
        PlayerPadel p2 = createPlayer(tournamentId);

        TeamPlayoffAddTeamRequest req = new TeamPlayoffAddTeamRequest();
        req.setPlayer1Id(p1.getId());
        req.setPlayer2Id(p2.getId());
        req.setRegistrationSource("ADMIN_MANUAL");
        req.setHasPaid(true);
        req.setAttended(true);

        return playoffService.addTeam(tournamentId, req);
    }

    private PlayerPadel createPlayer(Long tournamentId) {
        String suffix = tournamentId + "-" + UUID.randomUUID();
        return playerRepository.save(PlayerPadel.builder()
                .nombre("LFPT550")
                .apellido("Player" + suffix)
                .email("lfpt550-" + suffix + "@example.com")
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }
}
