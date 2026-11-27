package com.padle.core.padelcoreservice.mapper;

import com.padle.core.application.identity.OwnerAccount;
import com.padle.core.padelcoreservice.model.Owner;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OwnerPrincipalMapper {

    @Mapping(target = "password", ignore = true)
    Owner toPrincipal(OwnerAccount owner);
}
