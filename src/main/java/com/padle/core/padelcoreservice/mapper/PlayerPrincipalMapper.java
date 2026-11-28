package com.padle.core.padelcoreservice.mapper;

import com.padle.core.application.identity.PlayerIdentity;
import com.padle.core.application.identity.PlayerProfiles.Profile;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.padelcoreservice.security.PlayerPrincipal;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR, imports = PlayerIdentity.class)
public interface PlayerPrincipalMapper {

    PlayerPrincipalMapper INSTANCE = Mappers.getMapper(PlayerPrincipalMapper.class);

    @Mapping(target = "identity", expression = "java(new PlayerIdentity(player.getId(), player.getEmail()))")
    @Mapping(target = "password", source = "passwordHash")
    @Mapping(target = "authorities", ignore = true)
    @Mapping(target = "validContact", expression = "java(player.hasValidContact())")
    PlayerPrincipal toPrincipal(PlayerPadel player);

    @Mapping(target = "identity", expression = "java(new PlayerIdentity(profile.id(), profile.email()))")
    @Mapping(target = "password", source = "passwordHash")
    @Mapping(target = "authorities", ignore = true)
    @Mapping(target = "validContact", expression = "java(profile.hasValidContact())")
    PlayerPrincipal toPrincipal(Profile profile, String passwordHash);
}
