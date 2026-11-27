package com.padle.core.padelcoreservice.dto.calendar;

import com.padle.core.padelcoreservice.model.enums.EventFormat;
import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.TournamentStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record AdminCalendarMonth(String month, LocalDate today, int periodCount,
                                 List<Event> events, List<Venue> clubs, List<OrganizerOrganization> organizers) {
    public record Venue(Long id, String name) {}
    public record Event(Long id, LocalDate date, LocalTime time, LocalDate endDate, LocalTime endTime,
                        String timeZone, Long clubId, String clubName, EventFormat format,
                        GenderFormat gender, String level, TournamentStatus status, boolean active, OrganizerOrganization organizer) {}
}
