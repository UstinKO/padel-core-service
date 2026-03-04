package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.dto.RegistroRequestDto;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PlayerMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "fechaRegistro", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    @Mapping(target = "emailConfirmado", constant = "false")
    @Mapping(target = "fechaConfirmacionEmail", ignore = true)
    @Mapping(target = "codigoConfirmacion", ignore = true)
    @Mapping(target = "activo", constant = "true")
    PlayerPadel registroRequestToEntity(RegistroRequestDto request);

    PlayerResponseDto entityToResponse(PlayerPadel player);

    default PlayerPadel toEntity(RegistroRequestDto request, String passwordHash) {
        PlayerPadel player = registroRequestToEntity(request);
        player.setPasswordHash(passwordHash);
        return player;
    }
}