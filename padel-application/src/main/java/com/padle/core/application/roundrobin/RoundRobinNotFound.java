package com.padle.core.application.roundrobin;

public final class RoundRobinNotFound extends RuntimeException {
    public RoundRobinNotFound(Long tournamentId) {
        super("Tournament not found: " + tournamentId);
    }
}
