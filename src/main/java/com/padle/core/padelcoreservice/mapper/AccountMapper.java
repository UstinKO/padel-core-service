package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.AccountDtos;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    AccountDtos.Profile profile(PlayerPadel player);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target="nombre", source="nombre")
    @Mapping(target="apellido", source="apellido")
    @Mapping(target="email", source="email")
    @Mapping(target="telefono", source="telefono")
    @Mapping(target="telegramUsername", source="telegramUsername")
    @Mapping(target="nivelJugador", source="nivelJugador")
    @Mapping(target="preferredLocale", source="preferredLocale")
    void update(AccountDtos.ProfileUpdate request, @MappingTarget PlayerPadel player);
}
