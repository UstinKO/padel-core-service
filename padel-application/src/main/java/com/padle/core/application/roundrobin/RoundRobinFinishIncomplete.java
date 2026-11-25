package com.padle.core.application.roundrobin;

public final class RoundRobinFinishIncomplete extends RuntimeException {
    private final long remaining;

    public RoundRobinFinishIncomplete(long remaining) {
        this.remaining = remaining;
    }

    public long remaining() {
        return remaining;
    }
}
