package com.padle.core.padelcoreservice.service.calendar;

import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.dto.calendar.OrganizerOrganization;
import com.padle.core.padelcoreservice.service.OrganizerOrganizationService;
import java.util.Map;
import java.util.function.Function;
import com.padle.core.padelcoreservice.controller.admin.AdminEventFormat;
import com.padle.core.padelcoreservice.dto.calendar.AdminCalendarMonth;
import com.padle.core.padelcoreservice.dto.calendar.AdminCalendarMonth.Event;
import com.padle.core.padelcoreservice.dto.calendar.AdminCalendarMonth.Venue;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.mapper.AdminCalendarMapper;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.service.ClubAccountService;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCalendarService {
    private final TournamentService tournaments;
    private final ClubAccountService clubAccounts;
    private final AdminCalendarMapper mapper;
    private final OrganizerOrganizationService organizations;

    @Timed(name = "admin.calendar.month", tags = {"type=calendar"})
    public AdminCalendarMonth month(Owner owner, YearMonth month, Filters filters) {
        List<TournamentDto> visible = tournaments.getTournamentsForOwner(owner.getId(), owner.canViewAllTournaments());
        Map<Long, OrganizerOrganization> organizers = organizations.forEvents(visible).stream().collect(Collectors.toMap(OrganizerOrganization::id, Function.identity()));
        List<Event> period = visible.stream().filter(event -> event.getFechaInicio() != null)
                .map(source -> event(source, organizers)).filter(event -> overlaps(event, month))
                .sorted(Comparator.comparing(Event::date)
                        .thenComparing(Event::time, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Event::id)).toList();
        List<Event> selected = period.stream().filter(filters::matches).toList();
        return new AdminCalendarMonth(month.toString(), LocalDate.now(EventSchedule.zone(EventSchedule.DEFAULT_ZONE)),
                period.size(), selected, venues(owner, visible), organizers.values().stream()
                        .sorted(Comparator.comparing(OrganizerOrganization::name,
                                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                                .thenComparing(OrganizerOrganization::id)).toList());
    }

    private List<Venue> venues(Owner owner, List<TournamentDto> visible) {
        Stream<Venue> permitted = owner.canViewAllTournaments() || owner.isClubAdmin()
                ? clubAccounts.availableClubs(owner).stream().map(club -> new Venue(club.getId(), club.getNombre()))
                : Stream.empty();
        Stream<Venue> used = visible.stream().filter(event -> event.getClubId() != null)
                .map(event -> new Venue(event.getClubId(), event.getClubNombre()));
        return Stream.concat(permitted, used).collect(Collectors.toMap(Venue::id, venue -> venue,
                        (first, second) -> first, TreeMap::new)).values().stream()
                .sorted(Comparator.comparing(Venue::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList();
    }

    private Event event(TournamentDto source, Map<Long, OrganizerOrganization> organizers) {
        Event event = mapper.toEvent(source, organizers);
        if (source.getHoraInicio() == null) {
            return event;
        }
        return EventSchedule.duration(source).map(duration -> {
            var end = source.getFechaInicio().atTime(source.getHoraInicio()).plus(duration);
            return new Event(event.id(), event.date(), event.time(), end.toLocalDate(), end.toLocalTime(),
                    event.timeZone(), event.clubId(), event.clubName(), event.format(), event.gender(),
                    event.level(), event.status(), event.active(), event.organizer());
        }).orElse(event);
    }

    private static boolean overlaps(Event event, YearMonth month) {
        LocalDate lastDay = Optional.ofNullable(event.endDate()).orElse(event.date());
        if (LocalTime.MIDNIGHT.equals(event.endTime()) && lastDay.isAfter(event.date())) {
            lastDay = lastDay.minusDays(1);
        }
        return !event.date().isAfter(month.atEndOfMonth()) && !lastDay.isBefore(month.atDay(1));
    }

    public record Filters(Long clubId, AdminEventFormat format, GenderFormat gender, Nivel level, TournamentStatus status, Long organizerId) {
        boolean matches(Event event) {
            return (organizerId == null || event.organizer() != null && organizerId.equals(event.organizer().id()))
                    && (clubId == null || clubId.equals(event.clubId()))
                    && (format == null || format == event.format())
                    && (gender == null || gender == event.gender())
                    && (level == null || matchesLevel(event.level(), level))
                    && (status == null ? event.status() != TournamentStatus.CANCELADO : status == event.status());
        }

        private static boolean matchesLevel(String declared, Nivel level) {
            return level.name().equals(declared) || (declared != null && level.name().matches("[CD][0-9]")
                    && Arrays.asList(declared.split("_")).contains(level.name()));
        }
    }
}
