package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.PlayerSummaryDto;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PlayerSummaryMapper {

    @Mapping(target = "guestAccount", expression = "java(player.isGuestAccount())")
    PlayerSummaryDto toSummary(PlayerPadel player);
}
