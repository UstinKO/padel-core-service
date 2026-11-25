package com.padle.core.application.roundrobin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoundRobinFinishCommand {
    private final RoundRobinFinishPort port;
    private final RoundRobinPositionCommand positions;
    private final RoundRobinRankingQuery ranking;

    public RoundRobinFinishCommand(RoundRobinFinishPort port, RoundRobinPositionCommand positions,
                                   RoundRobinRankingQuery ranking) {
        this.port = port;
        this.positions = positions;
        this.ranking = ranking;
    }

    public RoundRobinRanking finish(Long tournamentId) {
        var event = port.findEvent(tournamentId).orElseThrow(() -> new RoundRobinNotFound(tournamentId));
        if (event.cancelled()) {
            throw new RoundRobinFinishCancelled();
        }
        long maximum = port.maximumRoundNumber(tournamentId);
        long completed = port.completedRounds(tournamentId);
        boolean initialized = port.hasCircleRound(tournamentId) && port.hasTeams(tournamentId);
        if (!initialized || completed < maximum) {
            throw new RoundRobinFinishIncomplete(maximum - completed);
        }

        positions.update(tournamentId);
        port.markFinished(event.id());
        return ranking.ranking(tournamentId);
    }
}
