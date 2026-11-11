package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.Format;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.Phase;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.State;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicEventStateService {
    private final AmericanoRoundRepository rounds;
    private final TournamentKingOfCourtRepository kings;
    private final AmericanoService americano;

    public State state(TournamentDto event) {
        var state = switch (event.getTipo()) {
            case KING_OF_COURT -> kingState(event.getId());
            case AMERICANO -> event.getModalidad() == Modalidad.DOBLES ? pairState(event.getId()) : americanoState(event.getId());
            case AMERICANO_TEAMS -> pairState(event.getId());
            case CANCHA_ABIERTA -> new State(Format.CANCHA_ABIERTA, Phase.NOT_STARTED, "");
            case PADEL_CLINIC -> new State(Format.PADEL_CLINIC, Phase.NOT_STARTED, "");
        };
        var phase = switch (event.getEstado()) {
            case CANCELADO -> Phase.CANCELLED;
            case FINALIZADO -> Phase.FINISHED;
            default -> state.phase();
        };
        return new State(state.format(), phase, state.livePath());
    }

    private State americanoState(Long id) {
        var initialized = americano.isInitialized(id);
        return new State(Format.AMERICANO, initialized ? Phase.LIVE : Phase.NOT_STARTED,
                initialized ? "/tournaments/americano/" + id : "");
    }

    private State kingState(Long id) {
        return kings.findFirstByTournamentIdOrderByIsActiveDescIdDesc(id)
                .map(king -> new State(Format.KING_OF_COURT,
                        Boolean.TRUE.equals(king.getIsFinished()) ? Phase.FINISHED
                                : Boolean.TRUE.equals(king.getIsActive()) ? Phase.LIVE : Phase.NOT_STARTED,
                        "/torneo/" + id + "/king-of-court"))
                .orElseGet(() -> new State(Format.KING_OF_COURT, Phase.NOT_STARTED, ""));
    }

    private State pairState(Long id) {
        if (rounds.existsByTournamentIdAndPhase(id, TournamentPhase.PLAYOFF)) {
            return new State(Format.TEAM_PLAYOFF, Phase.PLAYOFF, "/tournaments/team-playoff/" + id);
        }
        if (rounds.existsByTournamentIdAndPhase(id, TournamentPhase.QUALIFICATION)) {
            return new State(Format.TEAM_PLAYOFF, Phase.QUALIFICATION, "/tournaments/team-playoff/" + id);
        }
        if (rounds.existsByTournamentIdAndPhaseIsNull(id)) {
            return new State(Format.TEAM_AMERICANO, Phase.LIVE, "/tournaments/team-americano/" + id);
        }
        return new State(Format.TEAM_PAIRS, Phase.NOT_STARTED, "");
    }
}
