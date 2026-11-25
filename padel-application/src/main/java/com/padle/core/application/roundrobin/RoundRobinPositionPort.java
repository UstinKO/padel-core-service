package com.padle.core.application.roundrobin;

import java.util.List;

public interface RoundRobinPositionPort {
    RoundRobinPositionInput load(Long tournamentId);
    void savePositions(List<RoundRobinPosition> positions);
}
