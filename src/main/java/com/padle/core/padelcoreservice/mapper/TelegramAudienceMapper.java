package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.telegram.TelegramAudienceModels.*;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TelegramAudienceMapper {
    @Mapping(target = "playerId", source = "player.id")
    @Mapping(target = "name", source = "player.nombreCompleto")
    @Mapping(target = "category", source = "player.nivelJugador")
    @Mapping(target = "language", source = "player.locale.language")
    Candidate candidate(PlayerPadel player, Set<Group> groups, Availability availability);
}
