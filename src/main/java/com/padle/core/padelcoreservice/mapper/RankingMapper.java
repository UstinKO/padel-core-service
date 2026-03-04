package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.RankingDto;
import com.padle.core.padelcoreservice.model.Ranking;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RankingMapper {

    @Mapping(target = "playerNombre", ignore = true)
    @Mapping(target = "playerApellido", ignore = true)
    @Mapping(target = "playerNombreCompleto", ignore = true)
    @Mapping(target = "playerEmail", ignore = true)
    @Mapping(target = "winRate", ignore = true)
    @Mapping(target = "tendencia", ignore = true)
    RankingDto toDto(Ranking ranking);

    Ranking toEntity(RankingDto dto);
}