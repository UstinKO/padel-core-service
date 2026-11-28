package com.padle.core.padelcoreservice.adapter.roundrobin;

import com.padle.core.application.roundrobin.RoundRobinFinishEvent;
import com.padle.core.application.roundrobin.RoundRobinFinishPort;
import com.padle.core.padelcoreservice.mapper.americano.RoundRobinFinishMapper;
import com.padle.core.persistence.tournament.Tournament;
import com.padle.core.application.tournament.TournamentStatus;
import com.padle.core.persistence.tournament.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyRoundRobinFinishAdapter implements RoundRobinFinishPort {
    private final TournamentRepository tournaments;
    private final AmericanoRoundRepository rounds;
    private final AmericanoTeamRepository teams;
    private final RoundRobinFinishMapper mapper;
    private final EntityManager entities;

    @Override
    public Optional<RoundRobinFinishEvent> findEvent(Long tournamentId) {
        return tournaments.findById(tournamentId).map(mapper::toEvent);
    }

    @Override
    public long maximumRoundNumber(Long tournamentId) {
        return rounds.findMaxRoundNumber(tournamentId).orElse(0);
    }

    @Override
    public long completedRounds(Long tournamentId) {
        return rounds.countCompletedRounds(tournamentId);
    }

    @Override
    public boolean hasCircleRound(Long tournamentId) {
        return rounds.existsByTournamentIdAndPhaseIsNull(tournamentId);
    }

    @Override
    public boolean hasTeams(Long tournamentId) {
        return !teams.findByTournamentId(tournamentId).isEmpty();
    }

    @Override
    public void markFinished(Long tournamentId) {
        // findEvent загрузил событие в этот persistence context.
        var event = entities.getReference(Tournament.class, tournamentId);
        event.setEstado(TournamentStatus.FINALIZADO);
        tournaments.save(event);
    }
}
