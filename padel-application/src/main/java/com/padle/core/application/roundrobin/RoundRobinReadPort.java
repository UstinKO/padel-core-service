package com.padle.core.application.roundrobin;

import java.util.Optional;

public interface RoundRobinReadPort {
    Optional<RoundRobinReadSet> findByTournamentId(Long tournamentId);
}
