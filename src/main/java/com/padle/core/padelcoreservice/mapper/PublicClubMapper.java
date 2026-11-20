package com.padle.core.padelcoreservice.mapper;

import com.padle.core.contract.clubs.Club;
import com.padle.core.application.clubs.PublicClub;
import org.mapstruct.Mapper;
import org.mapstruct.Context;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.net.URI;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR,
        imports = URI.class)
public interface PublicClubMapper {
    @Mapping(target = "canonicalUrl", expression = "java(URI.create(baseUrl.replaceAll(\"/+$\", \"\") + \"/clubs/\" + club.id()))")
    Club toPublicDto(PublicClub club, @Context String baseUrl);
}
