package com.padle.core.padelcoreservice.service.calendar;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.calendar.PublicCalendarFeed.Availability;
import java.time.Instant;
import java.util.Objects;

public final class PublicRegistrationAvailability {
    private PublicRegistrationAvailability() {}

    public static Availability of(TournamentDto event, Instant now) {
        var start = event.getFechaInicio().atTime(event.getHoraInicio())
                .atZone(EventSchedule.zone(event.getTimeZone())).toInstant();
        if (!event.isRegistrationOpen() || !start.isAfter(now)) {
            return Availability.CLOSED;
        }
        int capacity = Objects.requireNonNullElse(event.getCupoMax(), 0);
        int occupied = Objects.requireNonNullElse(event.getInscritosActuales(), 0);
        if (capacity <= 0) {
            return Availability.AVAILABLE;
        }
        if (occupied >= capacity) {
            return Availability.WAITLIST;
        }
        return (long) occupied * 10 >= (long) capacity * 7 ? Availability.LIMITED : Availability.AVAILABLE;
    }
}
