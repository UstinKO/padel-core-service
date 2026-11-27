package com.padle.core.padelcoreservice.results;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ResultReport(Event tournament, List<Participant> participants, List<Game> matches,
                           List<String> warnings, Instant capturedAt, boolean contacts, boolean preliminary) {
    public record Event(long id, String name, LocalDate date, LocalTime time, String timeZone,
                        String organizer, String club, String category, String format, String modality,
                        String status, boolean active, String finalOrder) {}
    public record Person(Long id, String firstName, String lastName, String phone, String status,
                         String teamIds, String teamNumbers, String partnerIds, Boolean registrationActive) {}
    public record Participant(Person person, Standing standing, Integer finalPlace, String reachedStage) {}
    public record Standing(Integer position, Integer played, Integer wins, Integer losses, Integer draws,
                           Long matchPoints, Long courtBonus, Long adjustment, Long totalPoints, String unit) {
        public static final Standing EMPTY = new Standing(null, null, null, null, null, null, null, null, null, null);
    }
    public record Game(String source, long id, Long resultId, long tournamentId, Integer round, String stage,
                       String court, Long team1Id, String team1Players, String team1Names,
                       Long team2Id, String team2Players, String team2Names,
                       Integer score1, Integer score2, String scoreUnit, String sets,
                       String winnerPlayers, String status, String technicalResult) {}
    public record Preview(boolean preliminary, int participants, int matches, List<String> warnings) {}
}
