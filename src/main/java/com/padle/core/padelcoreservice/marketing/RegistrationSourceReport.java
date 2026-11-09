package com.padle.core.padelcoreservice.marketing;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record RegistrationSourceReport(LocalDate from, LocalDate to, Instant asOf, String model,
                                       int page, int pageSize, long totalGroups, Counts totals,
                                       long measuredParticipants, long measuredFirstRegistrations, long measuredPairs, List<Row> rows) {
    public record Counts(long participants, long firstRegistrations, long pairs,
                         long confirmed, long waitlist, long pending, long cancelled,
                         long firstConfirmed, long firstWaitlist, long firstPending, long firstCancelled,
                         long pairConfirmed, long pairWaitlist, long pairPending, long pairCancelled) { }
    public record Row(String source, String placement, String medium, String campaign,
                      String content, String term, long tournamentId, String tournament,
                      long clubId, Counts counts) { }
}
