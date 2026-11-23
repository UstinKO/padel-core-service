package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface TeamPlayingSideMapper {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "player1PlayingSide", source = "player1.playingSide")
    @Mapping(target = "player2PlayingSide", source = "player2.playingSide")
    void fill(AmericanoTeam team, @MappingTarget AmericanoTeamDto dto);
}
