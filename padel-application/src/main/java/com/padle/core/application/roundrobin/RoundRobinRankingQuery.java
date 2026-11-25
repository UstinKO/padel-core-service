package com.padle.core.application.roundrobin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoundRobinRankingQuery {
    private final RoundRobinReadPort port;

    public RoundRobinRankingQuery(RoundRobinReadPort port) {
        this.port = port;
    }

    public RoundRobinRanking ranking(Long tournamentId) {
        return RoundRobinRankingComposer.compose(port.findByTournamentId(tournamentId)
                .orElseThrow(() -> new RoundRobinNotFound(tournamentId)));
    }

    public RoundRobinRanking compose(RoundRobinReadSet values) {
        return RoundRobinRankingComposer.compose(values);
    }
}
