package com.padle.core.padelcoreservice.mapper;

import com.padle.core.application.clubs.ClubProfile;
import com.padle.core.padelcoreservice.dto.ClubDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ClubDtoMapper {
    @Mapping(target = "nombre", source = "fields.name")
    @Mapping(target = "direccion", source = "fields.address")
    @Mapping(target = "zonaCiudad", source = "fields.city")
    @Mapping(target = "telefonoContacto", source = "fields.phone")
    @Mapping(target = "emailContacto", source = "fields.email")
    @Mapping(target = "mapaUrl", source = "fields.mapUrl")
    @Mapping(target = "websiteUrl", source = "fields.websiteUrl")
    @Mapping(target = "descripcion", source = "fields.description")
    @Mapping(target = "logoUrl", source = "fields.logoUrl")
    @Mapping(target = "isActive", source = "active")
    ClubDto toDto(ClubProfile profile);

    List<ClubDto> toDto(List<ClubProfile> profiles);

    @Mapping(target = "name", source = "nombre")
    @Mapping(target = "address", source = "direccion")
    @Mapping(target = "city", source = "zonaCiudad")
    @Mapping(target = "phone", source = "telefonoContacto")
    @Mapping(target = "email", source = "emailContacto")
    @Mapping(target = "mapUrl", source = "mapaUrl")
    @Mapping(target = "description", source = "descripcion")
    ClubProfile.Fields toFields(ClubDto dto);
}
