package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.PublicTournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Whitelist of names already visible on the public tournament page. */
@Component
public class PublicParticipantsMapper {
    public PublicTournamentDto.Participants map(TournamentDto tournament,
                                                 List<TournamentRegistrationDto> registrations) {
        boolean doubles = tournament.getModalidad() == Modalidad.DOBLES;
        boolean teamPlayoff = tournament.getTipo() == TournamentType.AMERICANO_TEAMS
                || (tournament.getTipo() == TournamentType.AMERICANO && doubles);
        List<PublicTournamentDto.Participant> confirmed = new ArrayList<>();
        List<PublicTournamentDto.Participant> waitlist = new ArrayList<>();

        if (!doubles) {
            registrations.stream()
                    .filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED)
                    .map(r -> entry(r, false, false))
                    .forEach(confirmed::add);
        } else {
            // These are the same groups and ordering used by tournament-details.html.
            registrations.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsDoubleRegistration()))
                    .filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED
                            || r.getStatus() == RegistrationStatus.PARTNER_INVITED
                            || r.getStatus() == RegistrationStatus.PAIR_REGISTERED)
                    .filter(r -> teamPlayoff || r.getPartnerNombre() != null)
                    .map(r -> entry(r, true, false))
                    .forEach(confirmed::add);
            // Соло-заявки публично показывались в отдельных группах до изменения резерва.
            registrations.stream()
                    .filter(r -> r.getStatus() == RegistrationStatus.SOLO_ADD_LATER
                            || r.getStatus() == RegistrationStatus.SOLO_SEARCH)
                    .map(r -> entry(r, false, r.getStatus() == RegistrationStatus.SOLO_SEARCH))
                    .forEach(waitlist::add);
            // The site displays reserve names only for Team Americano pairs.
            if (teamPlayoff) {
                registrations.stream()
                        .filter(r -> Boolean.TRUE.equals(r.getIsDoubleRegistration()))
                        .filter(r -> r.getStatus() == RegistrationStatus.WAITLIST)
                        .map(r -> entry(r, true, false))
                        .forEach(waitlist::add);
            }
        }
        return new PublicTournamentDto.Participants(List.copyOf(confirmed), List.copyOf(waitlist));
    }

    private PublicTournamentDto.Participant entry(TournamentRegistrationDto registration,
                                                    boolean includePartner, boolean seekingPartner) {
        List<String> names = new ArrayList<>();
        names.add(fullName(registration.getPlayerNombre(), registration.getPlayerApellido()));
        if (includePartner && registration.getPartnerNombre() != null) {
            names.add(fullName(registration.getPartnerNombre(), registration.getPartnerApellido()));
        }
        return new PublicTournamentDto.Participant(
                "registration-" + registration.getId(), List.copyOf(names), seekingPartner);
    }

    private String fullName(String first, String last) {
        String name = ((first == null ? "" : first.trim()) + " "
                + (last == null ? "" : last.trim())).trim();
        if (name.isEmpty()) throw new IllegalStateException("Public participant has no name");
        return name;
    }
}
