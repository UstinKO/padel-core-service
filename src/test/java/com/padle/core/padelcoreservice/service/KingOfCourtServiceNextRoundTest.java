package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.mapper.KingOfCourtMapperImpl;
import com.padle.core.padelcoreservice.model.CourtTeam;
import com.padle.core.padelcoreservice.model.KingOfCourtCourt;
import com.padle.core.padelcoreservice.model.KingOfCourtMatchResult;
import com.padle.core.padelcoreservice.model.KingOfCourtPlayerStats;
import com.padle.core.padelcoreservice.model.KingOfCourtRound;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.repository.KingOfCourtCourtRepository;
import com.padle.core.padelcoreservice.repository.KingOfCourtMatchResultRepository;
import com.padle.core.padelcoreservice.repository.KingOfCourtPlayerStatsRepository;
import com.padle.core.padelcoreservice.repository.KingOfCourtRoundRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверяет {@link KingOfCourtService#nextRound(Long)} на реальном графе сущностей (без
 * Spring-контекста и БД, репозитории — Mockito-моки) — что история партнёрств, построенная
 * из фактически сыгранного раунда ({@code KingOfCourtPairingEngine}), действительно долетает до
 * выбора команд следующего раунда и позволяет избежать повтора, когда это возможно.
 */
@ExtendWith(MockitoExtension.class)
class KingOfCourtServiceNextRoundTest {

    @Mock private TournamentKingOfCourtRepository kingRepository;
    @Mock private KingOfCourtMatchResultRepository matchResultRepository;
    @Mock private KingOfCourtRoundRepository roundRepository;
    @Mock private KingOfCourtCourtRepository courtRepository;
    @Mock private KingOfCourtPlayerStatsRepository statsRepository;
    @Mock private TournamentRepository tournamentRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private WebSocketService webSocketService;
    @Mock private TournamentAccessService tournamentAccessService;

    private KingOfCourtService kingOfCourtService;

    private PlayerPadel a, b, c, d, e, f, g, h;
    private TournamentKingOfCourt king;
    private KingOfCourtRound round1;
    private List<KingOfCourtRound> persistedRounds;

    @BeforeEach
    void setUp() {
        kingOfCourtService = new KingOfCourtService(
                kingRepository, matchResultRepository, roundRepository, courtRepository,
                statsRepository, tournamentRepository, playerRepository,
                new KingOfCourtMapperImpl(), webSocketService, tournamentAccessService);

        a = player(1L);
        b = player(2L);
        c = player(3L);
        d = player(4L);
        e = player(5L);
        f = player(6L);
        g = player(7L);
        h = player(8L);

        king = new TournamentKingOfCourt();
        king.setId(100L);
        king.setTournament(Tournament.builder().id(1L).build());
        king.setMaxCourts(2);
        king.setCalibrationRounds(0);
        king.setCurrentRound(1);
        king.setIsActive(true);
        king.setIsFinished(false);
        king.getRounds().add(round1());

        round1 = king.getRounds().get(0);
        persistedRounds = new ArrayList<>(List.of(round1));

        when(kingRepository.findById(100L)).thenReturn(Optional.of(king));
        when(roundRepository.findByTournamentKingOrderByRoundNumberAsc(king))
                .thenAnswer(invocation -> new ArrayList<>(persistedRounds));
        when(roundRepository.save(any(KingOfCourtRound.class))).thenAnswer(invocation -> {
            KingOfCourtRound savedRound = invocation.getArgument(0);
            if (!persistedRounds.contains(savedRound)) {
                persistedRounds.add(savedRound);
            }
            return savedRound;
        });
        when(courtRepository.allResultsEntered(eq(round1.getId()))).thenReturn(true);
        when(statsRepository.findPlayersByTournamentKing(100L)).thenReturn(List.of(a, b, c, d, e, f, g, h));
        when(statsRepository.findRanking(100L)).thenReturn(List.of());
    }

    @Test
    void nextRound_whenJustWinningPairsMeetAgain_avoidsRepeatingTheirLastPartnership() {
        ArgumentCaptor<KingOfCourtCourt> savedCourts = ArgumentCaptor.forClass(KingOfCourtCourt.class);

        kingOfCourtService.nextRound(100L);

        verify(courtRepository, times(2)).save(savedCourts.capture());
        List<KingOfCourtCourt> createdCourts = savedCourts.getAllValues();
        assertThat(createdCourts).hasSize(2);

        KingOfCourtCourt topCourt = createdCourts.stream()
                .filter(court -> playersOf(court).equals(Set.of(a.getId(), b.getId(), e.getId(), f.getId())))
                .findFirst().orElseThrow();
        assertTeamsAvoidRepeat(topCourt, a, b, e, f);

        KingOfCourtCourt bottomCourt = createdCourts.stream()
                .filter(court -> playersOf(court).equals(Set.of(c.getId(), d.getId(), g.getId(), h.getId())))
                .findFirst().orElseThrow();
        assertTeamsAvoidRepeat(bottomCourt, c, d, g, h);
    }

    private void assertTeamsAvoidRepeat(KingOfCourtCourt court, PlayerPadel... playersWhoJustPaired) {
        assertThat(court.getTeams()).hasSize(2);
        Set<Set<Long>> newPairs = court.getTeams().stream()
                .map(team -> Set.of(team.getPlayer1().getId(), team.getPlayer2().getId()))
                .collect(Collectors.toSet());

        // Четверка состоит из двух пар, которые только что выиграли свои матчи вместе —
        // новое разбиение не должно быть точно тем же самым.
        Set<Long> firstPreviousPair = Set.of(playersWhoJustPaired[0].getId(), playersWhoJustPaired[1].getId());
        Set<Long> secondPreviousPair = Set.of(playersWhoJustPaired[2].getId(), playersWhoJustPaired[3].getId());
        assertThat(newPairs).isNotEqualTo(Set.of(firstPreviousPair, secondPreviousPair));

        Set<Long> coveredPlayers = newPairs.stream().flatMap(Set::stream).collect(Collectors.toSet());
        assertThat(coveredPlayers).containsExactlyInAnyOrder(
                playersWhoJustPaired[0].getId(), playersWhoJustPaired[1].getId(),
                playersWhoJustPaired[2].getId(), playersWhoJustPaired[3].getId());
    }

    private static Set<Long> playersOf(KingOfCourtCourt court) {
        return court.getPlayers().stream().map(PlayerPadel::getId).collect(Collectors.toSet());
    }

    private KingOfCourtRound round1() {
        KingOfCourtRound round = new KingOfCourtRound();
        round.setId(10L);
        round.setRoundNumber(1);
        round.setIsCompleted(true);

        KingOfCourtCourt court1 = courtWithResult(1001L, 1, List.of(a, b, c, d), a, b, c, d);
        KingOfCourtCourt court2 = courtWithResult(1002L, 2, List.of(e, f, g, h), e, f, g, h);
        court1.setRound(round);
        court2.setRound(round);
        court1.getResult().setCourt(court1);
        court2.getResult().setCourt(court2);
        round.setCourts(List.of(court1, court2));
        return round;
    }

    private KingOfCourtCourt courtWithResult(Long courtId, int courtNumber, List<PlayerPadel> players,
            PlayerPadel winner1, PlayerPadel winner2, PlayerPadel loser1, PlayerPadel loser2) {
        KingOfCourtCourt court = new KingOfCourtCourt();
        court.setId(courtId);
        court.setCourtNumber(courtNumber);
        court.setPlayers(new ArrayList<>(players));

        CourtTeam team1 = new CourtTeam();
        team1.setTeamNumber(1);
        team1.setPlayer1(winner1);
        team1.setPlayer2(winner2);
        CourtTeam team2 = new CourtTeam();
        team2.setTeamNumber(2);
        team2.setPlayer1(loser1);
        team2.setPlayer2(loser2);
        court.setTeams(List.of(team1, team2));

        KingOfCourtMatchResult result = new KingOfCourtMatchResult();
        result.setWinner1(winner1);
        result.setWinner2(winner2);
        result.setLoser1(loser1);
        result.setLoser2(loser2);
        result.setWinnersScore(21);
        result.setLosersScore(15);
        court.setResult(result);

        return court;
    }

    private static PlayerPadel player(Long id) {
        return PlayerPadel.builder().id(id).nombre("Jugador").apellido(String.valueOf(id)).build();
    }
}
