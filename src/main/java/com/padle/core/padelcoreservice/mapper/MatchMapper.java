package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.MatchDto;
import com.padle.core.padelcoreservice.model.Match;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MatchMapper {

    @Mapping(target = "tournamentNombre", ignore = true)
    @Mapping(target = "player1Nombre", ignore = true)
    @Mapping(target = "player1Apellido", ignore = true)
    @Mapping(target = "player1NombreCompleto", ignore = true)
    @Mapping(target = "player2Nombre", ignore = true)
    @Mapping(target = "player2Apellido", ignore = true)
    @Mapping(target = "player2NombreCompleto", ignore = true)
    @Mapping(target = "player3Nombre", ignore = true)
    @Mapping(target = "player3Apellido", ignore = true)
    @Mapping(target = "player3NombreCompleto", ignore = true)
    @Mapping(target = "player4Nombre", ignore = true)
    @Mapping(target = "player4Apellido", ignore = true)
    @Mapping(target = "player4NombreCompleto", ignore = true)
    @Mapping(target = "ganadorNombre", ignore = true)
    @Mapping(target = "perdedorNombre", ignore = true)
    @Mapping(target = "resultadoString", expression = "java(match.getResultadoString())")
    @Mapping(target = "totalSetsJugados", expression = "java(match.getTotalSetsJugados())")
    @Mapping(target = "isFinalizado", expression = "java(match.isFinalizado())")
    @Mapping(target = "isEnCurso", expression = "java(match.isEnCurso())")
    MatchDto toDto(Match match);

    Match toEntity(MatchDto dto);
}