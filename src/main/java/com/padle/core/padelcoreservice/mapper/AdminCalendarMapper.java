package com.padle.core.padelcoreservice.mapper;

import com.padle.core.application.identity.OwnerAccount;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.enums.EventFormat;
import com.padle.core.padelcoreservice.service.calendar.EventSchedule;
import com.padle.core.padelcoreservice.dto.calendar.AdminCalendarMonth.Event;
import org.mapstruct.Mapper;
import org.mapstruct.Context;
import com.padle.core.padelcoreservice.dto.calendar.OrganizerOrganization;
import com.padle.core.padelcoreservice.dto.calendar.OrganizationAccount;
import java.util.Map;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR,
        imports = {EventFormat.class, EventSchedule.class})
public interface AdminCalendarMapper {
    @Mapping(target = "timeZone", expression = "java(EventSchedule.zone(event.getTimeZone()).getId())")
    @Mapping(target = "date", source = "fechaInicio")
    @Mapping(target = "time", source = "horaInicio")
    @Mapping(target = "endDate", ignore = true)
    @Mapping(target = "endTime", ignore = true)
    @Mapping(target = "clubName", source = "clubNombre")
    @Mapping(target = "format", expression = "java(EventFormat.of(event.getTipo(), event.getModalidad()))")
    @Mapping(target = "gender", source = "generoFormato")
    @Mapping(target = "level", source = "categoriaNivel")
    @Mapping(target = "status", source = "estado")
    @Mapping(target = "active", source = "isActive", defaultValue = "false")
    @Mapping(target = "organizer", expression = "java(organizers.get(event.getOwnerId()))")
    Event toEvent(TournamentDto event, @Context Map<Long, OrganizerOrganization> organizers);

    @Mapping(target = "name", source = "organizationName")
    OrganizerOrganization toOrganizer(OwnerAccount owner);

    @Mapping(target = "accountName", source = "fullName")
    OrganizationAccount toOrganizationAccount(OwnerAccount owner);
}
