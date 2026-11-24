package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.calendar.OrganizerOrganization;
import com.padle.core.padelcoreservice.dto.calendar.PublicCalendarFeed.Event;
import com.padle.core.padelcoreservice.dto.calendar.PublicCalendarFeed.Participation;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.EventFormat;
import com.padle.core.padelcoreservice.service.calendar.EventSchedule;
import com.padle.core.padelcoreservice.service.calendar.PublicRegistrationAvailability;
import java.time.Instant;
import java.util.Map;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR,
        imports = {EventFormat.class, EventSchedule.class, PublicRegistrationAvailability.class})
public interface PublicCalendarMapper {
    @Mapping(target = "name", source = "nombre")
    @Mapping(target = "date", source = "fechaInicio")
    @Mapping(target = "time", source = "horaInicio")
    @Mapping(target = "timeZone", expression = "java(EventSchedule.zone(event.getTimeZone()).getId())")
    @Mapping(target = "format", expression = "java(EventFormat.of(event.getTipo(), event.getModalidad()))")
    @Mapping(target = "gender", source = "generoFormato")
    @Mapping(target = "level", source = "categoriaNivel")
    @Mapping(target = "club.id", source = "clubId")
    @Mapping(target = "club.name", source = "clubNombre")
    @Mapping(target = "organizer", expression = "java(organizers.get(event.getOwnerId()))")
    @Mapping(target = "status", source = "estado")
    @Mapping(target = "availability", expression = "java(PublicRegistrationAvailability.of(event, now))")
    @Mapping(target = "capacity", source = "cupoMax", defaultValue = "0")
    @Mapping(target = "occupied", source = "inscritosActuales", defaultValue = "0")
    @Mapping(target = "available", source = "disponibles")
    @Mapping(target = "waitlistCount", source = "waitlistCount", defaultValue = "0")
    @Mapping(target = "price", source = "precio")
    @Mapping(target = "currency", source = "moneda")
    @Mapping(target = "path", expression = "java(\"/torneo/\" + event.getId())")
    Event event(TournamentDto event, @Context Map<Long, OrganizerOrganization> organizers, @Context Instant now);

    @Mapping(target = "eventId", source = "tournament.id")
    Participation participation(TournamentRegistration registration);
}
