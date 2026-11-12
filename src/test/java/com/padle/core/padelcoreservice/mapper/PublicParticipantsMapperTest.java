package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.PublicTournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicParticipantsMapperTest {
    private final PublicParticipantsMapper mapper = new PublicParticipantsMapper();

    @Test
    void individualShowsOnlyConfirmedNames() {
        var roster = mapper.map(event(Modalidad.INDIVIDUAL, TournamentType.AMERICANO), List.of(
                registration(1, RegistrationStatus.CONFIRMED, false, "Ana", null),
                registration(2, RegistrationStatus.WAITLIST, false, "Bea", null),
                registration(3, RegistrationStatus.CANCELLED, false, "Cora", null)));
        assertThat(roster.confirmed()).containsExactly(
                new PublicTournamentDto.Participant("registration-1", List.of("Ana Surname"), false));
        // Individual reserve names are not shown on the site's public page.
        assertThat(roster.waitlist()).isEmpty();
    }

    @Test
    void teamAmericanoShowsPairsSearchAndOrderedReserve() {
        var roster = mapper.map(event(Modalidad.DOBLES, TournamentType.AMERICANO_TEAMS), List.of(
                registration(1, RegistrationStatus.CONFIRMED, true, "Ana", "Bea"),
                registration(2, RegistrationStatus.PARTNER_INVITED, true, "Cora", null),
                registration(3, RegistrationStatus.SOLO_ADD_LATER, false, "Dina", null),
                registration(4, RegistrationStatus.SOLO_SEARCH, false, "Eva", null),
                registration(5, RegistrationStatus.WAITLIST, true, "Fina", "Gina"),
                registration(6, RegistrationStatus.WAITLIST, true, "Hana", null),
                registration(7, RegistrationStatus.CANCELLED, true, "Iva", "Jana")));
        assertThat(roster.confirmed()).extracting(PublicTournamentDto.Participant::names)
                .containsExactly(List.of("Ana Surname", "Bea Surname"), List.of("Cora Surname"));
        assertThat(roster.waitlist().get(1).seekingPartner()).isTrue();
        assertThat(roster.waitlist()).extracting(PublicTournamentDto.Participant::names)
                .containsExactly(List.of("Dina Surname"), List.of("Eva Surname"),
                        List.of("Fina Surname", "Gina Surname"), List.of("Hana Surname"));
    }

    @Test
    void ordinaryDoublesDoesNotRevealUnlistedReserveOrIncompletePair() {
        var roster = mapper.map(event(Modalidad.DOBLES, TournamentType.KING_OF_COURT), List.of(
                registration(1, RegistrationStatus.CONFIRMED, true, "Ana", "Bea"),
                registration(2, RegistrationStatus.PARTNER_INVITED, true, "Cora", null),
                registration(3, RegistrationStatus.WAITLIST, true, "Dina", "Eva")));
        assertThat(roster.confirmed()).hasSize(1);
        assertThat(roster.waitlist()).isEmpty();
    }

    @Test
    void emptyRosterIsExplicit() {
        assertThat(mapper.map(event(Modalidad.INDIVIDUAL, TournamentType.AMERICANO), List.of()))
                .isEqualTo(new PublicTournamentDto.Participants(List.of(), List.of()));
    }

    private TournamentDto event(Modalidad modality, TournamentType type) {
        return TournamentDto.builder().modalidad(modality).tipo(type).build();
    }

    private TournamentRegistrationDto registration(long id, RegistrationStatus status, boolean doubles,
                                                   String player, String partner) {
        var r = new TournamentRegistrationDto();
        r.setId(id);
        r.setStatus(status);
        r.setIsDoubleRegistration(doubles);
        r.setPlayerNombre(player);
        r.setPlayerApellido("Surname");
        r.setPartnerNombre(partner);
        r.setPartnerApellido(partner == null ? null : "Surname");
        r.setPlayerEmail("private@example.test");
        r.setPlayerPhone("+5491112345678");
        r.setPlayerTelegram("private_handle");
        return r;
    }
}
