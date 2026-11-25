package com.padle.core.application.roundrobin;

import java.util.List;
import com.padle.core.application.roundrobin.RoundRobinReadSet.Phase;
import com.padle.core.application.roundrobin.RoundRobinReadSet.ScoringMode;
import com.padle.core.application.roundrobin.RoundRobinReadSet.Status;

/** Значения для круговых мест; порядок команд определяет исходные индексы engines. */
public record RoundRobinPositionInput(ScoringMode mode, List<PositionTeam> teams, List<PositionMatch> matches) {
    public record PositionTeam(Long id, Integer number, Integer totalScore, Integer matchesWon,
                               Integer pointsScored, Integer pointsConceded, Integer matchesLost,
                               Integer gameDifference) {}

    public record PositionMatch(Long firstId, Long secondId, Phase phase, Status status,
                                Integer firstScore, Integer secondScore) {}
}
