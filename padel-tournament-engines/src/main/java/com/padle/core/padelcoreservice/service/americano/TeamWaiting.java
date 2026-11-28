package com.padle.core.padelcoreservice.service.americano;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

final class TeamWaiting {
    record Interval(long id, LocalDateTime started, LocalDateTime completed, boolean terminal) {}
    record Waiting(Long currentSeconds, long maximumSeconds, long totalSeconds, LocalDateTime since) {}

    static Waiting calculate(List<Interval> matches, LocalDateTime now) {
        var finished = matches.stream().filter(m -> m.completed() != null)
                .sorted(Comparator.comparing(Interval::completed)).toList();
        long maximum = 0;
        long total = 0;
        Long current = null;
        LocalDateTime since = null;
        for (var previous : finished) {
            if (previous.terminal()) continue;
            var next = matches.stream().filter(m -> m.id() != previous.id() && m.started() != null
                            && !m.started().isBefore(previous.completed()))
                    .map(Interval::started).min(Comparator.naturalOrder());
            long seconds = Math.max(0, Duration.between(previous.completed(), next.orElse(now)).getSeconds());
            maximum = Math.max(maximum, seconds);
            total += seconds;
            if (next.isEmpty()) {
                current = seconds;
                since = previous.completed();
            }
        }
        return new Waiting(current, maximum, total, since);
    }
}
