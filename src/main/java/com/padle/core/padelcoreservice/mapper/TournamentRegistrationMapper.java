package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TournamentRegistrationMapper {

    @Mapping(source = "player.id", target = "playerId")
    @Mapping(source = "player.nombre", target = "playerNombre")
    @Mapping(source = "player.apellido", target = "playerApellido")
    @Mapping(source = "player.email", target = "playerEmail")
    @Mapping(source = "tournament.id", target = "tournamentId")
    TournamentRegistrationDto toDto(TournamentRegistration registration);

    TournamentRegistration toEntity(TournamentRegistrationDto dto);
}