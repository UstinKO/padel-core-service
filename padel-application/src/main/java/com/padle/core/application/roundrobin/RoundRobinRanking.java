package com.padle.core.application.roundrobin;

import java.util.List;

public record RoundRobinRanking(Long tournamentId, String tournamentName, int totalRounds, int completedRounds,
                                 int totalTeams, boolean finished, RoundRobinReadSet.ScoringMode scoringMode,
                                 int pointsPerMatch, int courts, boolean roundRobinComplete, List<RankedTeam> ranking,
                                 List<AdditionalMatch> additionalMatches, List<FinalStanding> finalRanking) {
    public record RankedTeam(RoundRobinReadSet.Team team, int position) {}

    public record AdditionalMatch(Long id, Integer placementStart, String label, Integer courtNumber,
                                  Long team1Id, Integer team1Number, String team1Name,
                                  Long team2Id, Integer team2Number, String team2Name,
                                  Integer team1Score, Integer team2Score, RoundRobinReadSet.Status status) {}

    public record FinalStanding(Long teamId, Integer teamNumber, String displayName, int position) {}
}
