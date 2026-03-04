package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Tournament;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TournamentMapper {

    @Mapping(target = "clubNombre", ignore = true) // Игнорируем при маппинге из Entity в DTO, будем заполнять отдельно
    TournamentDto toDto(Tournament tournament);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    Tournament toEntity(TournamentDto tournamentDto);
}