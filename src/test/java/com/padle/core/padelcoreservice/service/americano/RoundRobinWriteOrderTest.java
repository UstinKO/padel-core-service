package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.application.roundrobin.RoundRobinRankingQuery;
import com.padle.core.padelcoreservice.adapter.roundrobin.LegacyRoundRobinReadAdapter;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoRankingDto;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.mapper.americano.RoundRobinRankingMapper;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoundRobinWriteOrderTest {
    private static final Long ID = 42L;
    @Mock RoundRobinRankingQuery query;
    @Mock LegacyRoundRobinReadAdapter read;
    @Mock RoundRobinRankingMapper mapper;
    @Mock TournamentRepository tournaments;
    @Mock TournamentRegistrationRepository registrations;
    @Mock AmericanoTeamRepository teams;
    @Mock AmericanoRoundRepository rounds;
    @Mock AmericanoMatchRepository matches;
    @InjectMocks TeamAmericanoService service;

    @Test
    void emptyPositionsDoNotRequireAnEventOrInitialization() {
        service.updateTeamPositions(ID);
        var calls = inOrder(rounds, teams, matches);
        calls.verify(rounds).findByTournamentIdOrderByRoundNumberAsc(ID);
        calls.verify(teams).findByTournamentId(ID);
        calls.verify(matches).findByTournamentIdOrderByRoundIdAscMatchNumberAsc(ID);
        calls.verify(teams).saveAll(List.of());
        calls.verifyNoMoreInteractions();
        verifyNoInteractions(tournaments, registrations, query, read, mapper);
    }

    @Test
    void nullModeUsesPointsAndAppliesPositionsToTheOriginalUnsortedTeams() {
        var low = team(1, 1);
        var high = team(2, 2);
        high.setTotalScore(20);
        when(rounds.findByTournamentIdOrderByRoundNumberAsc(ID)).thenReturn(List.of(new AmericanoRound()));
        when(teams.findByTournamentId(ID)).thenReturn(List.of(low, high));
        service.updateTeamPositions(ID);
        assertThat(low.getCurrentPosition()).isEqualTo(2);
        assertThat(high.getCurrentPosition()).isEqualTo(1);
        verify(teams).saveAll(List.of(high, low));
        assertThat(low.getMatchesWon()).isZero();
        assertThat(high.getTotalScore()).isEqualTo(20);
    }

    @Test
    void setsKeepsSharedPositionsAndSaveOrderWithoutReplacingEntities() {
        var first = team(1, 1);
        var second = team(2, 2);
        var third = team(3, 3);
        first.setMatchesWon(1);
        second.setMatchesWon(1);
        var round = new AmericanoRound();
        round.setScoringMode(RoundRobinScoringMode.SETS);
        when(rounds.findByTournamentIdOrderByRoundNumberAsc(ID)).thenReturn(List.of(round));
        when(teams.findByTournamentId(ID)).thenReturn(List.of(third, second, first));
        service.updateTeamPositions(ID);
        assertThat(List.of(first, second, third)).extracting(AmericanoTeam::getCurrentPosition)
                .containsExactly(1, 1, 3);
        verify(teams).saveAll(List.of(first, second, third));
    }

    @Test
    void cancellationStopsBeforeReadingCounters() {
        when(tournaments.findById(ID)).thenReturn(Optional.of(event(TournamentStatus.CANCELADO)));
        assertThatThrownBy(() -> service.finishTournament(ID)).isInstanceOf(InvalidStateException.class)
                .hasMessage("round_robin.error.finished");
        verifyNoInteractions(rounds, teams, matches, query, mapper);
    }

    @Test
    void setsUsesCircleMeetingsButIgnoresPlacementWhenSavingPlaces() {
        var first = team(1, 1);
        var second = team(2, 2);
        var third = team(3, 3);
        first.setMatchesWon(2);
        second.setMatchesWon(2);
        var circle = new AmericanoRound();
        circle.setScoringMode(RoundRobinScoringMode.SETS);
        var placement = new AmericanoRound();
        placement.setPhase(TournamentPhase.PLACEMENT);
        var meeting = AmericanoMatch.builder().round(circle).team1Id(2L).team2Id(1L)
                .team1Score(6).team2Score(4).status(AmericanoRoundStatus.COMPLETED).build();
        var extra = AmericanoMatch.builder().round(placement).team1Id(1L).team2Id(2L)
                .team1Score(6).team2Score(0).status(AmericanoRoundStatus.COMPLETED).build();
        when(rounds.findByTournamentIdOrderByRoundNumberAsc(ID)).thenReturn(List.of(circle, placement));
        when(teams.findByTournamentId(ID)).thenReturn(List.of(third, first, second));
        when(matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(ID)).thenReturn(List.of(extra, meeting));
        service.updateTeamPositions(ID);
        verify(teams).saveAll(List.of(second, first, third));
        assertThat(List.of(first, second, third)).extracting(AmericanoTeam::getCurrentPosition)
                .containsExactly(2, 1, 3);
    }

    @Test
    void absentCircleShortCircuitsTeamsButCountersAreReadFirst() {
        when(tournaments.findById(ID)).thenReturn(Optional.of(event(TournamentStatus.CERRADO)));
        when(rounds.findMaxRoundNumber(ID)).thenReturn(Optional.of(5));
        when(rounds.countCompletedRounds(ID)).thenReturn(2);
        assertThatThrownBy(() -> service.finishTournament(ID)).isInstanceOf(InvalidStateException.class)
                .hasMessage("No se puede finalizar. Quedan 3 rondas sin completar.");
        var calls = inOrder(tournaments, rounds);
        calls.verify(tournaments).findById(ID);
        calls.verify(rounds).findMaxRoundNumber(ID);
        calls.verify(rounds).countCompletedRounds(ID);
        calls.verify(rounds).existsByTournamentIdAndPhaseIsNull(ID);
        calls.verifyNoMoreInteractions();
        verifyNoInteractions(teams, matches, query);
    }

    @Test
    void pendingCounterStillChecksInitializationBeforeRejecting() {
        when(tournaments.findById(ID)).thenReturn(Optional.of(event(TournamentStatus.CERRADO)));
        when(rounds.findMaxRoundNumber(ID)).thenReturn(Optional.of(5));
        when(rounds.countCompletedRounds(ID)).thenReturn(2);
        when(rounds.existsByTournamentIdAndPhaseIsNull(ID)).thenReturn(true);
        when(teams.findByTournamentId(ID)).thenReturn(List.of(team(1, 1)));
        assertThatThrownBy(() -> service.finishTournament(ID)).isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("Quedan 3");
        verify(teams).findByTournamentId(ID);
        verifyNoMoreInteractions(teams);
        verifyNoInteractions(matches, query);
    }

    @Test
    void finishWritesPositionsThenStateAndOnlyThenComposesTheResponse() {
        var event = event(TournamentStatus.FINALIZADO);
        var team = team(1, 1);
        var response = TeamAmericanoRankingDto.builder().tournamentId(ID).build();
        when(tournaments.findById(ID)).thenReturn(Optional.of(event));
        when(rounds.findMaxRoundNumber(ID)).thenReturn(Optional.of(1));
        when(rounds.countCompletedRounds(ID)).thenReturn(1);
        when(rounds.existsByTournamentIdAndPhaseIsNull(ID)).thenReturn(true);
        when(teams.findByTournamentId(ID)).thenReturn(List.of(team));
        when(mapper.toDto(any())).thenReturn(response);
        assertThat(service.finishTournament(ID)).isSameAs(response);
        assertThat(team.getCurrentPosition()).isEqualTo(1);
        var calls = inOrder(tournaments, rounds, teams, matches, query, mapper);
        calls.verify(tournaments).findById(ID);
        calls.verify(rounds).findMaxRoundNumber(ID);
        calls.verify(rounds).countCompletedRounds(ID);
        calls.verify(rounds).existsByTournamentIdAndPhaseIsNull(ID);
        calls.verify(teams).findByTournamentId(ID);
        calls.verify(rounds).findByTournamentIdOrderByRoundNumberAsc(ID);
        calls.verify(teams).findByTournamentId(ID);
        calls.verify(matches).findByTournamentIdOrderByRoundIdAscMatchNumberAsc(ID);
        calls.verify(teams).saveAll(List.of(team));
        calls.verify(tournaments).save(event);
        calls.verify(query).ranking(ID);
        calls.verify(mapper).toDto(any());
        calls.verifyNoMoreInteractions();
    }

    private AmericanoTeam team(long id, int number) {
        return AmericanoTeam.builder().id(id).teamNumber(number).currentPosition(9).build();
    }

    private Tournament event(TournamentStatus state) {
        return Tournament.builder().id(ID).estado(state).build();
    }
}
