package com.padle.core.padelcoreservice.mapper;

import com.padle.core.application.identity.PlayerProfiles;
import com.padle.core.padelcoreservice.dto.AccountDtos;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountMapper {
    AccountDtos.Profile profile(PlayerProfiles.Profile player);
}
