package com.padle.core.persistence.identity;

import com.padle.core.application.identity.OwnerAccount;
import com.padle.core.application.identity.OwnerAccounts.Profile;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OwnerAccountMapper {

    OwnerAccount toAccount(OwnerEntity entity);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "isActive", source = "isActive")
    void updateProfile(Profile profile, @MappingTarget OwnerEntity entity);
}
