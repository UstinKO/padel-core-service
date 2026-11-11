package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.PublicEventResponse;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.Action;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.Organizer;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.State;
import com.padle.core.padelcoreservice.dto.PublicTournamentDto;
import org.mapstruct.Mapper;
import org.mapstruct.Named;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PublicEventMapper {
    @Mapping(target = "event", source = "event")
    @Mapping(target = "organizer", source = "organizer")
    @Mapping(target = "format", source = "state.format")
    @Mapping(target = "phase", source = "state.phase")
    @Mapping(target = "canonicalUrl", source = "event.websiteUrl")
    @Mapping(target = "pngUrl", ignore = true)
    PublicEventResponse toResponse(PublicTournamentDto event, Organizer organizer, State state,
            List<Action> actions, String calendarUrl);

    @Mapping(target = "contact", source = "contact")
    @Mapping(target = "whatsappUrl", expression = "java(whatsappUrl(contact))")
    Organizer toOrganizer(String contact);

    @Named("publicWhatsapp")
    default String whatsappUrl(String contact) {
        if (contact == null || !contact.matches("\\+?[0-9 ()-]{7,25}")) { return ""; }
        var digits = contact.replaceAll("[^0-9]", "");
        return digits.matches("[0-9]{7,15}") ? "https://wa.me/" + digits : "";
    }
}
