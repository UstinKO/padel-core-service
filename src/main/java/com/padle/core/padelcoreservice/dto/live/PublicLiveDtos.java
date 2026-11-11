package com.padle.core.padelcoreservice.dto.live;

import com.padle.core.padelcoreservice.dto.AdditionalPointsDto;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.Format;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.Phase;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import java.time.Instant;
import java.util.List;

public final class PublicLiveDtos {
    private PublicLiveDtos() {}
    public record Metadata(long eventId, String name, Phase phase, String eventStatus, Long instanceId,
                           long conductVersion, Instant generatedAt, String canonicalUrl, boolean showLevels,
                           String topic, int pollSeconds) {}
    public record Participant(Long id, String name, Nivel level) {}
    public record Side(Long teamId, Integer number, String name, List<Participant> players,
                       Integer score, Integer games, Integer matchOrdinal) {
        public Side { players = List.copyOf(players); }
    }
    public record Match(Long id, Integer number, Integer court, String phase, String stage, String status,
                        Side first, Side second, boolean completed, Boolean firstWinner, Long nextMatchId,
                        Integer nextSlot, boolean priority, Integer queuePosition, Long waitingMinutes) {}
    public record Round(Long id, Integer number, String phase, String status, Integer courts,
                        List<Match> matches, List<String> byePlayerNames) {
        public Round { matches = List.copyOf(matches); byePlayerNames = List.copyOf(byePlayerNames); }
    }
    public record KingRank(Long playerId, String playerName, Nivel playerNivel, Integer rank, Long totalPoints,
                           Long matchPoints, Integer bonusPoints, AdditionalPointsDto additionalPoints,
                           Integer gamesPlayed, Integer wins, Integer losses) {}
    public record AmericanoRank(Long playerId, String playerName, Nivel playerNivel, Integer position,
                                Long totalScore, Long matchPoints, AdditionalPointsDto additionalPoints,
                                Integer matchesPlayed, Integer matchesWon, Integer matchesLost, Integer matchesDrawn,
                                Integer byeCount, Integer pointsScored, Integer pointsConceded, Integer pointDifference) {}
    public record TeamRank(Long id, Integer teamNumber, String displayName, Participant player1, Participant player2,
                           Integer currentPosition, Integer setsWon, Integer setsLost, Integer gamesWon, Integer gamesLost,
                           Integer matchesPlayed, Integer matchesWon, Integer matchesLost, Integer matchesDrawn,
                           Integer match1GamesWon, Integer match1GamesLost, Integer match2GamesWon, Integer match2GamesLost,
                           String qualStatus, String tournamentStatus, String eliminatedStage,
                           Integer queuePosition, String queueOpponentName) {}
    public record King(Metadata metadata, Format format, Integer currentRound, Integer calibrationRounds,
                       List<Round> rounds, List<KingRank> ranking) implements PublicLiveSnapshot {
        public King { rounds = List.copyOf(rounds); ranking = List.copyOf(ranking); }
    }
    public record Americano(Metadata metadata, Format format, String formatType, List<Round> rounds,
                            List<AmericanoRank> ranking) implements PublicLiveSnapshot {
        public Americano { rounds = List.copyOf(rounds); ranking = List.copyOf(ranking); }
    }
    public record Playoff(Metadata metadata, Format format, List<Round> rounds, List<TeamRank> ranking,
                          List<Match> queue) implements PublicLiveSnapshot {
        public Playoff { rounds = List.copyOf(rounds); ranking = List.copyOf(ranking); queue = List.copyOf(queue); }
    }
    public record Pending(Metadata metadata, Format format, List<Round> rounds) implements PublicLiveSnapshot {
        public Pending { rounds = List.copyOf(rounds); }
    }
    public record Invalidation(long eventId, String type) {}
}
