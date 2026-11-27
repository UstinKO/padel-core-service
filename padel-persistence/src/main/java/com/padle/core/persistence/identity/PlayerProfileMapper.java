package com.padle.core.persistence.identity;

import com.padle.core.application.identity.PlayerProfiles.Profile;
import com.padle.core.application.identity.PlayerProfiles.ProfileUpdate;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
interface PlayerProfileMapper {

    Profile toProfile(PlayerPadel player);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "nombre", source = "nombre")
    @Mapping(target = "apellido", source = "apellido")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "telefono", source = "telefono")
    @Mapping(target = "telegramUsername", source = "telegramUsername")
    @Mapping(target = "nivelJugador", source = "nivelJugador")
    @Mapping(target = "preferredLocale", source = "preferredLocale")
    void update(ProfileUpdate update, @MappingTarget PlayerPadel player);
}
