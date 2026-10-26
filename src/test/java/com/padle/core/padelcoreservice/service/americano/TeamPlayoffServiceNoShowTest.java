package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.AmericanoPlayerStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.service.WebSocketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LFPT-0729: marcar un equipo de Team Playoff como "no se presentó" — no debe tocar equipos
 * con partidos de calificación ya creados, y debe excluirse de inmediato del conteo de equipos
 * activos que usan {@code canInitPlayoff}/{@code initPlayoff} para el tamaño del bracket.
 */
class TeamPlayoffServiceNoShowTest {

    private final AmericanoTeamRepository teamRepository = mock(AmericanoTeamRepository.class);
    private final AmericanoMatchRepository matchRepository = mock(AmericanoMatchRepository.class);
    private final AmericanoRoundRepository roundRepository = mock(AmericanoRoundRepository.class);
    private final TournamentRepository tournamentRepository = mock(TournamentRepository.class);

    private final TeamPlayoffService service = new TeamPlayoffService(
            tournamentRepository,
            teamRepository,
            roundRepository,
            matchRepository,
            mock(PlayerRepository.class),
            mock(PlayerService.class),
            mock(TournamentRegistrationRepository.class),
            mock(WebSocketService.class),
            mock(PlayoffMatchingEngine.class)
    );

    private Tournament tournament;
    private AmericanoTeam team;

    @BeforeEach
    void setUp() {
        tournament = Tournament.builder().id(100L).build();
        team = AmericanoTeam.builder()
                .id(1L)
                .tournament(tournament)
                .teamNumber(1)
                .status(AmericanoPlayerStatus.ACTIVE)
                .build();
        when(teamRepository.findById(1L)).thenReturn(java.util.Optional.of(team));
        when(teamRepository.save(any(AmericanoTeam.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void markNoShow_equipoSinPartidos_pasaAEstadoNoShow() {
        when(matchRepository.findByTournamentIdAndPhase(100L, TournamentPhase.QUALIFICATION))
                .thenReturn(List.of());
        when(teamRepository.findByTournamentIdAndStatus(100L, AmericanoPlayerStatus.ACTIVE))
                .thenReturn(List.of());

        AmericanoTeam result = service.markNoShow(1L);

        assertThat(result.getStatus()).isEqualTo(AmericanoPlayerStatus.NO_SHOW);
        verify(teamRepository).save(team);
    }

    @Test
    void markNoShow_equipoConPartidoDeCalificacion_lanzaExcepcionYNoCambiaEstado() {
        AmericanoMatch existingMatch = AmericanoMatch.builder().id(10L).team1Id(1L).team2Id(2L).build();
        when(matchRepository.findByTournamentIdAndPhase(100L, TournamentPhase.QUALIFICATION))
                .thenReturn(List.of(existingMatch));

        assertThatThrownBy(() -> service.markNoShow(1L))
                .isInstanceOf(InvalidStateException.class);

        assertThat(team.getStatus()).isEqualTo(AmericanoPlayerStatus.ACTIVE);
    }

    @Test
    void markNoShow_equipoYaNoActivo_lanzaExcepcion() {
        team.setStatus(AmericanoPlayerStatus.DROPPED_OUT);

        assertThatThrownBy(() -> service.markNoShow(1L))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void cancelNoShow_equipoMarcadoComoNoShow_vuelveAActivo() {
        team.setStatus(AmericanoPlayerStatus.NO_SHOW);

        AmericanoTeam result = service.cancelNoShow(1L);

        assertThat(result.getStatus()).isEqualTo(AmericanoPlayerStatus.ACTIVE);
    }

    @Test
    void cancelNoShow_equipoNoMarcado_lanzaExcepcion() {
        team.setStatus(AmericanoPlayerStatus.ACTIVE);

        assertThatThrownBy(() -> service.cancelNoShow(1L))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void canInitPlayoff_equipoNoShowNoCuentaParaElTamanoDelBracket() {
        AmericanoTeam active1 = teamWithStatus(1L, AmericanoPlayerStatus.ACTIVE, 2);
        AmericanoTeam active2 = teamWithStatus(2L, AmericanoPlayerStatus.ACTIVE, 2);
        AmericanoTeam noShow = teamWithStatus(3L, AmericanoPlayerStatus.NO_SHOW, 0);

        when(roundRepository.findByTournamentIdAndPhase(100L, TournamentPhase.PLAYOFF)).thenReturn(List.of());
        when(roundRepository.findByTournamentIdAndPhase(100L, TournamentPhase.QUALIFICATION))
                .thenReturn(List.of(mock(com.padle.core.padelcoreservice.model.americano.AmericanoRound.class)));
        when(teamRepository.findPlayoffRankingByTournamentId(100L))
                .thenReturn(List.of(active1, active2, noShow));

        boolean canInit = service.canInitPlayoff(100L);

        // 2 equipos activos con 2 partidos cada uno (settled) ya es suficiente para el bracket
        // de potencia de 2 — el equipo NO_SHOW (0 partidos) no debe impedirlo ni contarse.
        assertThat(canInit).isTrue();
    }

    private AmericanoTeam teamWithStatus(Long id, AmericanoPlayerStatus status, int matchesPlayed) {
        return AmericanoTeam.builder()
                .id(id)
                .tournament(tournament)
                .teamNumber(id.intValue())
                .status(status)
                .matchesPlayed(matchesPlayed)
                .build();
    }
}
