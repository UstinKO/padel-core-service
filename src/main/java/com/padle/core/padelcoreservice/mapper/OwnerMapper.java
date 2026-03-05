package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.OwnerDto;
import com.padle.core.padelcoreservice.model.Owner;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OwnerMapper {

    OwnerDto toDto(Owner owner);
}