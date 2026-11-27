package com.padle.core.persistence.clubs;

import com.padle.core.application.clubs.ClubDetails;
import com.padle.core.application.clubs.PublicClub;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ClubReadModelMapper {
    @Mapping(target = "name", source = "nombre")
    @Mapping(target = "address", source = "direccion")
    @Mapping(target = "city", source = "zonaCiudad")
    @Mapping(target = "description", source = "descripcion")
    @Mapping(target = "mapUrl", source = "mapaUrl")
    PublicClub toReadModel(Club club);

    @Mapping(target = "name", source = "nombre")
    @Mapping(target = "address", source = "direccion")
    @Mapping(target = "formattedAddress", source = "direccionCompleta")
    @Mapping(target = "active", source = "isActive")
    ClubDetails toDetails(Club club);
}
