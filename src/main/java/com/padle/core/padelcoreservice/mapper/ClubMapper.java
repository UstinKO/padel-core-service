package com.padle.core.padelcoreservice.mapper;

import com.padle.core.application.clubs.ClubProfile;
import com.padle.core.padelcoreservice.model.Club;
import org.mapstruct.InheritConfiguration;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ClubMapper {

    @Mapping(target = "fields", source = "club")
    @Mapping(target = "active", source = "isActive")
    ClubProfile toProfile(Club club);

    @InheritInverseConfiguration(name = "toEntity")
    ClubProfile.Fields toFields(Club club);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "isActive", ignore = true)
    @Mapping(target = "nombre", source = "name")
    @Mapping(target = "direccion", source = "address")
    @Mapping(target = "zonaCiudad", source = "city")
    @Mapping(target = "telefonoContacto", source = "phone")
    @Mapping(target = "emailContacto", source = "email")
    @Mapping(target = "mapaUrl", source = "mapUrl")
    @Mapping(target = "descripcion", source = "description")
    Club toEntity(ClubProfile.Fields fields);

    @InheritConfiguration(name = "toEntity")
    void update(ClubProfile.Fields fields, @MappingTarget Club club);
}
