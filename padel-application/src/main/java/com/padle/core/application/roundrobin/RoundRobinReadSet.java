package com.padle.core.application.roundrobin;

import java.util.List;

/** Материализованные значения прежних источников чтения; порядок списков сохраняется. */
public record RoundRobinReadSet(Event event, List<Team> rankingTeams, List<Round> rounds,
                                List<Match> allMatches, List<Team> additionalTeams,
                                List<Match> additionalMatches) {
    public record Event(Long id, String name) {}

    public record Team(Long id, Long tournamentId, Integer number, String displayName, TeamStatus status,
                       Player player1, Player player2, String guestName, String guestPhone, Statistics statistics) {}

    public record Player(Long id, String name, String email, String phone, String level, Side side) {}

    public record Statistics(Integer totalScore, Integer played, Integer wins, Integer losses, Integer drawn,
                             Integer pointsScored, Integer pointsConceded, Integer setsWon, Integer setsLost,
                             Integer gamesWon, Integer gamesLost) {
        public Integer pointDifference() { return pointsScored - pointsConceded; }
        public Integer gameDifference() { return gamesWon - gamesLost; }
    }

    public record Round(Phase phase, Status status, ScoringMode scoringMode, Integer pointsPerMatch, Integer courts) {}

    public record Match(Long id, Long firstId, Long secondId, Phase phase, Status status,
                        Integer firstScore, Integer secondScore, Integer court, Integer placementStart, String label) {}

    public enum ScoringMode { POINTS, SETS }
    public enum Phase { QUALIFICATION, PLAYOFF, PLACEMENT }
    public enum Status { PENDING, QUEUED, IN_PROGRESS, COMPLETED, CANCELLED }
    public enum TeamStatus { ACTIVE, DROPPED_OUT, DISQUALIFIED, NO_SHOW }
    public enum Side { LEFT, RIGHT, EITHER }
}
