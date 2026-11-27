package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.application.roundrobin.RoundRobinPosition;
import com.padle.core.application.roundrobin.RoundRobinReadSet.ScoringMode;
import com.padle.core.padelcoreservice.adapter.roundrobin.LegacyRoundRobinPositionAdapter;
import com.padle.core.padelcoreservice.mapper.americano.RoundRobinPositionMapper;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyRoundRobinPositionAdapterTest {
    @Mock AmericanoRoundRepository rounds;
    @Mock AmericanoTeamRepository teams;
    @Mock AmericanoMatchRepository matches;
    @Mock EntityManager entities;
    private LegacyRoundRobinPositionAdapter adapter;

    @BeforeEach
    void connectTheActualMapper() {
        adapter = new LegacyRoundRobinPositionAdapter(rounds, teams, matches,
                Mappers.getMapper(RoundRobinPositionMapper.class), entities);
    }

    @Test
    void pointsReadsMatchesButDoesNotMapThemProfilesOrNullableGameDifference() {
        var player = mock(PlayerPadel.class);
        var match = mock(AmericanoMatch.class);
        var team = AmericanoTeam.builder().id(1L).teamNumber(2).player1(player)
                .gamesWon(null).gamesLost(null).pointsScored(null).matchesWon(null).totalScore(10).build();
        when(teams.findByTournamentId(42L)).thenReturn(List.of(team));
        when(matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(42L)).thenReturn(List.of(match));
        var input = adapter.load(42L);
        assertThat(input.mode()).isEqualTo(ScoringMode.POINTS);
        assertThat(input.teams()).singleElement().satisfies(value -> {
            assertThat(value.id()).isEqualTo(1L);
            assertThat(value.totalScore()).isEqualTo(10);
            assertThat(value.matchesWon()).isNull();
            assertThat(value.pointsScored()).isNull();
            assertThat(value.gameDifference()).isNull();
        });
        assertThat(input.matches()).isEmpty();
        var calls = inOrder(rounds, teams, matches);
        calls.verify(rounds).findByTournamentIdOrderByRoundNumberAsc(42L);
        calls.verify(teams).findByTournamentId(42L);
        calls.verify(matches).findByTournamentIdOrderByRoundIdAscMatchNumberAsc(42L);
        calls.verifyNoMoreInteractions();
        verifyNoInteractions(player, match, entities);
    }

    @Test
    void emptySetsDoesNotVisitMatchObjectsAndStillSavesAnEmptyList() {
        var round = new AmericanoRound();
        round.setScoringMode(RoundRobinScoringMode.SETS);
        var match = mock(AmericanoMatch.class);
        when(rounds.findByTournamentIdOrderByRoundNumberAsc(42L)).thenReturn(List.of(round));
        when(matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(42L)).thenReturn(List.of(match));
        var input = adapter.load(42L);
        assertThat(input.mode()).isEqualTo(ScoringMode.SETS);
        assertThat(input.matches()).isEmpty();
        adapter.savePositions(List.of());
        verify(teams).saveAll(List.of());
        verifyNoInteractions(match, entities);
    }

    @Test
    void appliesOnlyCurrentPositionAndSavesTheSameReference() {
        var team = AmericanoTeam.builder().id(7L).teamNumber(3).currentPosition(9).totalScore(20)
                .matchesPlayed(4).hasPaid(true).attended(true).adminComment("QA").build();
        when(entities.getReference(AmericanoTeam.class, 7L)).thenReturn(team);
        adapter.savePositions(List.of(new RoundRobinPosition(0, 7L, 2)));
        assertThat(team.getCurrentPosition()).isEqualTo(2);
        assertThat(team.getTotalScore()).isEqualTo(20);
        assertThat(team.getTeamNumber()).isEqualTo(3);
        assertThat(team.getMatchesPlayed()).isEqualTo(4);
        assertThat(team.getHasPaid()).isTrue();
        assertThat(team.getAttended()).isTrue();
        assertThat(team.getAdminComment()).isEqualTo("QA");
        verify(entities).getReference(AmericanoTeam.class, 7L);
        verifyNoMoreInteractions(entities);
        verify(teams).saveAll(List.of(team));
        verifyNoInteractions(rounds, matches);
    }
}
