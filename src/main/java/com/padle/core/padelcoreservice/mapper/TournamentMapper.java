package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.persistence.tournament.Tournament;
import com.padle.core.application.tournament.Nivel;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.MappingTarget;
import com.padle.core.padelcoreservice.service.contact.OrganizerContacts;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TournamentMapper {

    @Mapping(target = "organizerContact", ignore = true)
    @Mapping(target = "clubNombre", ignore = true) // Игнорируем при маппинге из Entity в DTO, будем заполнять отдельно
    @Mapping(target = "cancellationDeadlineFormatted", expression = "java(tournament.getFormattedCancellationDeadline())")
    TournamentDto toDto(Tournament tournament);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isActive", constant = "true")
    @Mapping(target = "testTournament", ignore = true)
    @Mapping(target = "organizerContactOriginal", ignore = true)
    @Mapping(target = "ownerId", source = "ownerId") // Добавляем маппинг ownerId
    Tournament toEntity(TournamentDto tournamentDto);

    @AfterMapping
    default void prepareOrganizerContact(Tournament source, @MappingTarget TournamentDto target) {
        var contact = OrganizerContacts.resolve(source);
        target.setOrganizerContact(contact);
        target.setOrganizerContactOriginal(source.getOrganizerContactOriginal() == null
                ? source.getContactoOrganizador() : source.getOrganizerContactOriginal());
        if (contact.valid()) {
            target.setOrganizerName(contact.name());
            target.setOrganizerPhone(contact.phone());
            target.setOrganizerCountry(contact.country());
        }
    }

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "organizerName", source = "name")
    @Mapping(target = "organizerPhone", source = "phone")
    @Mapping(target = "organizerCountry", source = "country")
    @Mapping(target = "contactoOrganizador", source = "phone")
    void applyOrganizerContact(OrganizerContacts.Contact contact, @MappingTarget Tournament target);

    default Nivel toNivel(String value) {
        // MapStruct сохраняет NULL для отсутствующего необязательного значения.
        return value == null ? null : Nivel.fromValue(value);
    }
}
