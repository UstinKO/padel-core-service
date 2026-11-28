package com.padle.core.padelcoreservice.service.calendar;

import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.calendar.OrganizerOrganization;
import com.padle.core.padelcoreservice.dto.calendar.PublicCalendarFeed;
import com.padle.core.padelcoreservice.dto.calendar.PublicCalendarFeed.Facets;
import com.padle.core.padelcoreservice.dto.calendar.PublicCalendarFeed.Personal;
import com.padle.core.padelcoreservice.dto.calendar.PublicCalendarFeed.Venue;
import com.padle.core.padelcoreservice.dto.calendar.PublicCalendarFilter;
import com.padle.core.padelcoreservice.exception.PublicDiscoveryException;
import com.padle.core.padelcoreservice.mapper.PublicCalendarMapper;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.EventFormat;
import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.application.registration.RegistrationStatus;
import com.padle.core.padelcoreservice.repository.PublicCatalogSpecification;
import com.padle.core.persistence.tournament.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.service.OrganizerOrganizationService;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicCalendarService {
    private final TournamentService tournaments;
    private final OrganizerOrganizationService organizations;
    private final TournamentRegistrationRepository registrations;
    private final PublicCalendarMapper mapper;

    @Timed(name = "public.calendar.month", tags = {"type=calendar"})
    public PublicCalendarFeed month(String month, PublicCalendarFilter filters) {
        var range = PublicCalendarRange.of(month);
        return feed(range.month().toString(), range.from(), range.to(), filters);
    }

    @Timed(name = "public.calendar.catalog", tags = {"type=calendar"})
    public PublicCalendarFeed catalog(PublicCalendarFilter filters) {
        return feed("", null, null, filters);
    }

    @Timed(name = "public.calendar.personal", tags = {"type=calendar"})
    public Personal personal(PlayerPadel actor, String month) {
        Optional<PublicCalendarRange> range = Optional.ofNullable(month).map(PublicCalendarRange::of);
        var own = registrations.findForPlayerEvents(actor.getId()).stream()
                .filter(row -> Boolean.TRUE.equals(row.getIsActive()) && !row.isCancelled()
                        && row.getStatus() != RegistrationStatus.SOLO_EXPIRED)
                .filter(row -> PublicCatalogSpecification.publiclyListed(row.getTournament()))
                .filter(row -> range.map(value -> value.contains(row.getTournament().getFechaInicio())).orElse(true))
                .map(mapper::participation).toList();
        return new Personal(actor.getId(), own);
    }

    private PublicCalendarFeed feed(String month, LocalDate from, LocalDate to, PublicCalendarFilter filters) {
        validate(filters);
        List<TournamentDto> visible = tournaments.getPublicCalendarEvents(from, to);
        List<OrganizerOrganization> names = organizations.forEvents(visible);
        Map<Long, OrganizerOrganization> byId = names.stream()
                .collect(Collectors.toMap(OrganizerOrganization::id, Function.identity()));
        Instant now = Instant.now();
        var events = visible.stream().filter(event -> matches(event, filters))
                .map(event -> mapper.event(event, byId, now)).toList();
        return new PublicCalendarFeed(month, LocalDate.now(EventSchedule.zone(null)), from, to,
                events, facets(visible, names));
    }

    private static boolean matches(TournamentDto event, PublicCalendarFilter filters) {
        return (filters.clubId() == null || filters.clubId().equals(event.getClubId()))
                && (filters.organizerId() == null || filters.organizerId().equals(event.getOwnerId()))
                && (filters.format() == null || filters.format() == EventFormat.of(event.getTipo(), event.getModalidad()))
                && (filters.gender() == null || filters.gender() == event.getGeneroFormato())
                && (filters.level() == null || DeclaredEventLevel.matches(event.getCategoriaNivel(), filters.level()));
    }

    private static Facets facets(List<TournamentDto> visible, List<OrganizerOrganization> names) {
        var venues = visible.stream().filter(event -> event.getClubId() != null)
                .map(event -> new Venue(event.getClubId(), event.getClubNombre())).distinct()
                .sorted(Comparator.comparing(Venue::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Venue::id)).toList();
        return new Facets(venues, names, List.of(EventFormat.values()), List.of(GenderFormat.values()),
                Arrays.stream(Nivel.values()).filter(level -> level != Nivel.SIN_ESPECIFICAR).toList());
    }

    private static void validate(PublicCalendarFilter filters) {
        if (filters.clubId() != null && filters.clubId() < 1
                || filters.organizerId() != null && filters.organizerId() < 1) {
            throw new PublicDiscoveryException("INVALID_CATALOG_FILTER");
        }
    }
}
