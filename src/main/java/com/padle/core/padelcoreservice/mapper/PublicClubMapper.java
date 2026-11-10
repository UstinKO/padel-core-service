package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.PublicClubResponse;
import com.padle.core.padelcoreservice.model.Club;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PublicClubMapper {
    @Mapping(target = "name", source = "nombre")
    @Mapping(target = "address", source = "direccion")
    @Mapping(target = "city", source = "zonaCiudad")
    @Mapping(target = "description", source = "descripcion")
    @Mapping(target = "mapUrl", source = "mapaUrl")
    PublicClubResponse toPublicDto(Club club);
}
