package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.PlayerEvents.Event;
import com.padle.core.padelcoreservice.model.Tournament;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@Mapper(componentModel = "spring")
public interface PlayerEventsMapper {
    @Mapping(target = "name", source = "tournament.nombre")
    @Mapping(target = "type", source = "tournament.tipo")
    @Mapping(target = "status", source = "tournament.estado")
    @Mapping(target = "modality", source = "tournament.modalidad")
    @Mapping(target = "gender", source = "tournament.generoFormato")
    @Mapping(target = "level", source = "tournament.categoriaNivel")
    @Mapping(target = "startsAt", source = "tournament")
    Event event(Tournament tournament, String clubName);

    default OffsetDateTime startsAt(Tournament tournament) {
        return tournament.getFechaInicio().atTime(tournament.getHoraInicio())
                .atZone(ZoneId.of(tournament.getTimeZone())).toOffsetDateTime();
    }
}
