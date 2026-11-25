package com.padle.core.application.roundrobin;

import java.util.Optional;

public interface RoundRobinFinishPort {
    Optional<RoundRobinFinishEvent> findEvent(Long tournamentId);
    long maximumRoundNumber(Long tournamentId);
    long completedRounds(Long tournamentId);
    boolean hasCircleRound(Long tournamentId);
    boolean hasTeams(Long tournamentId);
    void markFinished(Long tournamentId);
}
