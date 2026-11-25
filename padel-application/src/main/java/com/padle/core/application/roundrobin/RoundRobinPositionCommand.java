package com.padle.core.application.roundrobin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoundRobinPositionCommand {
    private final RoundRobinPositionPort port;

    public RoundRobinPositionCommand(RoundRobinPositionPort port) {
        this.port = port;
    }

    @Transactional
    public void update(Long tournamentId) {
        port.savePositions(RoundRobinStandingsCalculation.rank(port.load(tournamentId)));
    }
}
