package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.SpaSessionResponse;
import com.padle.core.padelcoreservice.dto.SpaSessionUserType;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Set;

@Mapper(componentModel = "spring", imports = SpaSessionUserType.class)
public interface SpaSessionMapper {

    Set<String> SUPPORTED_LOCALES = Set.of("es", "ru", "en");

    @Mapping(target = "authenticated", constant = "true")
    @Mapping(target = "userType", expression = "java(SpaSessionUserType.PLAYER)")
    @Mapping(target = "id", source = "player.id")
    @Mapping(target = "fullName", source = "player.nombreCompleto")
    @Mapping(target = "email", source = "player.email")
    @Mapping(target = "roles", source = "roles")
    @Mapping(target = "clubId", ignore = true)
    @Mapping(target = "hasValidContact", expression = "java(player.hasValidContact())")
    @Mapping(target = "preferredLocale", source = "player.preferredLocale", qualifiedByName = "supportedLocale")
    SpaSessionResponse fromPlayer(PlayerPadel player, List<String> roles);

    @Mapping(target = "authenticated", constant = "true")
    @Mapping(target = "userType", expression = "java(SpaSessionUserType.OWNER)")
    @Mapping(target = "id", source = "owner.id")
    @Mapping(target = "fullName", source = "owner.fullName")
    @Mapping(target = "email", source = "owner.email")
    @Mapping(target = "roles", source = "roles")
    @Mapping(target = "clubId", source = "owner.clubId")
    @Mapping(target = "hasValidContact", ignore = true)
    @Mapping(target = "preferredLocale", ignore = true)
    SpaSessionResponse fromOwner(Owner owner, List<String> roles);

    @Named("supportedLocale")
    default String supportedLocale(String locale) {
        return SUPPORTED_LOCALES.contains(locale) ? locale : null;
    }
}
