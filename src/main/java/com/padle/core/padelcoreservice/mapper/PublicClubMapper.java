package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.PublicClubResponse;
import com.padle.core.application.clubs.PublicClub;
import org.mapstruct.Mapper;
import org.mapstruct.Context;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PublicClubMapper {
    @Mapping(target = "canonicalUrl", expression = "java(baseUrl.replaceAll(\"/+$\", \"\") + \"/clubs/\" + club.id())")
    PublicClubResponse toPublicDto(PublicClub club, @Context String baseUrl);
}
