package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.PlayingSideDtos;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = Nivel.class)
public interface PlayingSideMapper {
    @Mapping(target = "playerId", source = "id")
    @Mapping(target = "level", source = "nivelJugador")
    PlayingSideDtos.Participant participant(PlayerPadel player);

    @Mapping(target = "ask", source = "ask")
    @Mapping(target = "levelMissing", expression = "java(player.getNivelJugador() == null || player.getNivelJugador() == Nivel.SIN_ESPECIFICAR)")
    PlayingSideDtos.State state(PlayerPadel player, boolean ask);
}
