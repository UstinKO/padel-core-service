package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.context.annotation.Primary;

@Primary
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TournamentRegistrationMapper {

    @Mapping(source = "player.id", target = "playerId")
    @Mapping(source = "player.nombre", target = "playerNombre")
    @Mapping(source = "player.apellido", target = "playerApellido")
    @Mapping(source = "player.email", target = "playerEmail")
    @Mapping(source = "player.telefono", target = "playerPhone")
    @Mapping(source = "player.telegramUsername", target = "playerTelegram")
    @Mapping(source = "tournament.id", target = "tournamentId")
    @Mapping(source = "tournament.nombre", target = "tournamentNombre")
    @Mapping(source = "partner.id", target = "partnerId")
    @Mapping(target = "partnerNombre", expression = "java(getPartnerNombre(registration))")
    @Mapping(target = "partnerApellido", expression = "java(getPartnerApellido(registration))")
    @Mapping(target = "partnerEmail", expression = "java(getPartnerEmail(registration))")
    @Mapping(target = "partnerPhone", source = "partnerPhone")
    @Mapping(target = "partnerTelegram", expression = "java(getPartnerTelegram(registration))")
    @Mapping(target = "partnerRegistered", expression = "java(registration.getPartner() != null)")
    @Mapping(source = "shareContacts", target = "shareContacts")
    TournamentRegistrationDto toDto(TournamentRegistration registration);

    TournamentRegistration toEntity(TournamentRegistrationDto dto);

    default String getPartnerNombre(TournamentRegistration registration) {
        if (registration.getPartner() != null) {
            return registration.getPartner().getNombre();
        }
        return registration.getPartnerFirstName();
    }

    default String getPartnerApellido(TournamentRegistration registration) {
        if (registration.getPartner() != null) {
            return registration.getPartner().getApellido();
        }
        return registration.getPartnerLastName();
    }

    default String getPartnerEmail(TournamentRegistration registration) {
        if (registration.getPartner() != null) {
            return registration.getPartner().getEmail();
        }
        return registration.getPartnerEmail();
    }

    default String getPartnerTelegram(TournamentRegistration registration) {
        if (registration.getPartner() != null) {
            return registration.getPartner().getTelegramUsername();
        }
        return null;
    }
}