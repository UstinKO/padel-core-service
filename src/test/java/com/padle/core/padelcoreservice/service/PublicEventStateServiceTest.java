package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.Format;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.Phase;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicEventStateServiceTest {
    @Mock AmericanoRoundRepository rounds;
    @Mock TournamentKingOfCourtRepository kings;
    @Mock AmericanoService americano;
    @InjectMocks PublicEventStateService service;

    @Test
    void pairFormatIsNotGuessedBeforeInitialization() {
        var state = service.state(event(TournamentType.AMERICANO_TEAMS));
        assertThat(state.format()).isEqualTo(Format.TEAM_PAIRS);
        assertThat(state.phase()).isEqualTo(Phase.NOT_STARTED);
        assertThat(state.livePath()).isEmpty();
    }

    @Test
    void qualificationAndPlayoffRemainDistinctFromRoundRobin() {
        when(rounds.existsByTournamentIdAndPhase(42L, TournamentPhase.PLAYOFF)).thenReturn(false);
        when(rounds.existsByTournamentIdAndPhase(42L, TournamentPhase.QUALIFICATION)).thenReturn(true);
        var state = service.state(event(TournamentType.AMERICANO_TEAMS));
        assertThat(state.format()).isEqualTo(Format.TEAM_PLAYOFF);
        assertThat(state.phase()).isEqualTo(Phase.QUALIFICATION);
        when(rounds.existsByTournamentIdAndPhase(42L, TournamentPhase.PLAYOFF)).thenReturn(true);
        assertThat(service.state(event(TournamentType.AMERICANO_TEAMS)).phase()).isEqualTo(Phase.PLAYOFF);
    }

    @Test
    void roundsWithoutPhaseBelongToRoundRobin() {
        when(rounds.existsByTournamentIdAndPhaseIsNull(42L)).thenReturn(true);
        var state = service.state(event(TournamentType.AMERICANO_TEAMS));
        assertThat(state.format()).isEqualTo(Format.TEAM_AMERICANO);
        assertThat(state.phase()).isEqualTo(Phase.LIVE);
        assertThat(state.livePath()).isEqualTo("/tournaments/team-americano/42");
    }

    @Test
    void terminalStatusOverridesConductState() {
        var event = event(TournamentType.CANCHA_ABIERTA);
        event.setEstado(TournamentStatus.CANCELADO);
        assertThat(service.state(event).phase()).isEqualTo(Phase.CANCELLED);
        event.setEstado(TournamentStatus.FINALIZADO);
        assertThat(service.state(event).phase()).isEqualTo(Phase.FINISHED);
    }

    @Test
    void kingFinishedStateSurvivesDeactivation() {
        var king = new TournamentKingOfCourt();
        king.setIsActive(false); king.setIsFinished(true);
        when(kings.findFirstByTournamentIdOrderByIsActiveDescIdDesc(42L)).thenReturn(Optional.of(king));
        assertThat(service.state(event(TournamentType.KING_OF_COURT)).phase()).isEqualTo(Phase.FINISHED);
    }

    private TournamentDto event(TournamentType type) {
        return TournamentDto.builder().id(42L).tipo(type).modalidad(Modalidad.DOBLES)
                .estado(TournamentStatus.REGISTRO_ABIERTO).build();
    }
}
