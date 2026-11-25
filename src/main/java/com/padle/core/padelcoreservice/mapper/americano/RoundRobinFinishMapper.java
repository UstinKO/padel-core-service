package com.padle.core.padelcoreservice.mapper.americano;

import com.padle.core.application.roundrobin.RoundRobinFinishEvent;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface RoundRobinFinishMapper {
    @Mapping(target = "cancelled", source = "estado", qualifiedByName = "cancelled")
    RoundRobinFinishEvent toEvent(Tournament tournament);

    @Named("cancelled")
    default boolean cancelled(TournamentStatus status) {
        return status == TournamentStatus.CANCELADO;
    }
}
