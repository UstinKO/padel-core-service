package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.PublicTournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/** Маппинг {@link TournamentDto} → публичный DTO для мобильного клиента. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PublicTournamentMapper {
    ZoneId EVENT_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Mapping(target = "name", source = "nombre")
    @Mapping(target = "club", source = "tournament", qualifiedByName = "toPublicClub")
    @Mapping(target = "type", source = "tipo")
    @Mapping(target = "modality", source = "modalidad")
    @Mapping(target = "gender", source = "generoFormato")
    @Mapping(target = "level", source = "categoriaNivel")
    @Mapping(target = "startsAt", expression = "java(toOffset(tournament.getFechaInicio().atTime(tournament.getHoraInicio())))")
    @Mapping(target = "timeZone", expression = "java(EVENT_ZONE.getId())")
    @Mapping(target = "duration", source = "duracion")
    @Mapping(target = "price", source = "precio")
    @Mapping(target = "currency", source = "moneda")
    @Mapping(target = "status", source = "estado")
    @Mapping(target = "registrationOpen", expression = "java(tournament.isRegistrationOpen())")
    @Mapping(target = "capacity", expression = "java(nonNegative(tournament.getCupoMax()))")
    @Mapping(target = "capacityUnit", expression = "java(capacityUnit(tournament.getModalidad()))")
    @Mapping(target = "occupied", expression = "java(nonNegative(tournament.getInscritosActuales()))")
    @Mapping(target = "available", source = "disponibles")
    @Mapping(target = "waitlistCount", expression = "java(nonNegative(tournament.getWaitlistCount()))")
    @Mapping(target = "cancellationDeadline", expression = "java(toOffset(tournament.getDeadlineCancelacion()))")
    @Mapping(target = "description", source = "infoDetallada")
    @Mapping(target = "websiteUrl", expression = "java(websiteUrl(baseUrl, tournament.getId()))")
    PublicTournamentDto toPublicDto(TournamentDto tournament, @Context String baseUrl);

    @Named("toPublicClub")
    default PublicTournamentDto.Club toPublicClub(TournamentDto tournament) {
        return new PublicTournamentDto.Club(
                tournament.getClubId(), tournament.getClubNombre(), tournament.getClubDireccion());
    }

    default OffsetDateTime toOffset(java.time.LocalDateTime value) {
        return Optional.ofNullable(value).map(date -> date.atZone(EVENT_ZONE).toOffsetDateTime()).orElse(null);
    }

    default int nonNegative(Integer value) {
        return Math.max(0, Objects.requireNonNullElse(value, 0));
    }

    default PublicTournamentDto.CapacityUnit capacityUnit(Modalidad modality) {
        return modality == Modalidad.DOBLES ? PublicTournamentDto.CapacityUnit.PAIRS : PublicTournamentDto.CapacityUnit.PLAYERS;
    }

    default String websiteUrl(String baseUrl, Long tournamentId) {
        return baseUrl.replaceAll("/+$", "") + "/torneo/" + tournamentId;
    }
}
