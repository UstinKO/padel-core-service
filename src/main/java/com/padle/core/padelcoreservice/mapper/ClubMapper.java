package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.ClubDto;
import com.padle.core.padelcoreservice.model.Club;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ClubMapper {

    ClubDto toDto(Club club);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    Club toEntity(ClubDto clubDto);
}