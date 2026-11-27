package com.padle.core.padelcoreservice.dto.calendar;

import com.padle.core.padelcoreservice.model.enums.EventFormat;
import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record PublicCalendarFeed(String month, LocalDate today, LocalDate from, LocalDate to,
        List<Event> events, Facets facets) {
    public PublicCalendarFeed { events = List.copyOf(events); }

    public record Event(Long id, String name, LocalDate date, LocalTime time, String timeZone,
            EventFormat format, GenderFormat gender, String level, Venue club, OrganizerOrganization organizer,
            TournamentStatus status, Availability availability, int capacity, int occupied, int available,
            int waitlistCount, BigDecimal price, String currency, String path) {}

    public record Venue(Long id, String name) {}
    public enum Availability { AVAILABLE, LIMITED, WAITLIST, CLOSED }

    public record Facets(List<Venue> clubs, List<OrganizerOrganization> organizers, List<EventFormat> formats,
            List<GenderFormat> genders, List<Nivel> levels) {
        public Facets {
            clubs = List.copyOf(clubs); organizers = List.copyOf(organizers); formats = List.copyOf(formats);
            genders = List.copyOf(genders); levels = List.copyOf(levels);
        }
    }

    public record Participation(Long eventId, RegistrationStatus status) {}
    public record Personal(Long playerId, List<Participation> registrations) {
        public Personal { registrations = List.copyOf(registrations); }
    }
}
