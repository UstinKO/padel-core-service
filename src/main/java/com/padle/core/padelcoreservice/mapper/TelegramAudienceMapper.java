package com.padle.core.padelcoreservice.mapper;

import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.padelcoreservice.telegram.TelegramAudienceModels.*;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TelegramAudienceMapper {
    @Mapping(target = "playerId", source = "player.id")
    @Mapping(target = "name", source = "player.nombreCompleto")
    @Mapping(target = "category", source = "player.nivelJugador")
    Candidate candidate(PlayerPadel player, Set<Group> groups, Availability availability);
}
