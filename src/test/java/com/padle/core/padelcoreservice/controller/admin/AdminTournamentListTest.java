package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.service.ClubService;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.TournamentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminTournamentListTest {
    private static final LocalDate FROM = LocalDate.of(2026, 11, 12);
    private static final LocalDate TO = LocalDate.of(2026, 11, 15);

    @Mock private TournamentService tournaments;
    @Mock private TournamentAccessService access;
    @Mock private ClubService clubs;
    @InjectMocks private AdminController controller;

    @Test
    void combinesClubAndFormatWithInclusiveStartDates() {
        Owner admin = owner(OwnerRole.SUPER_ADMIN);
        TournamentDto first = event(1, 3, 8, FROM, TournamentType.AMERICANO_TEAMS, Modalidad.DOBLES);
        TournamentDto last = event(2, 3, 8, TO, TournamentType.AMERICANO, Modalidad.DOBLES);
        when(tournaments.getAllTournaments()).thenReturn(List.of(first, last,
                event(3, 3, 8, TO, TournamentType.AMERICANO, Modalidad.INDIVIDUAL),
                event(4, 9, 8, TO, TournamentType.AMERICANO_TEAMS, Modalidad.DOBLES),
                event(5, 3, 8, TO.plusDays(1), TournamentType.AMERICANO_TEAMS, Modalidad.DOBLES)));
        doCallRealMethod().when(access).canManage(any(), any(), any());

        var model = list(admin, 3L, AdminEventFormat.AMERICANO_PAIRS, FROM, TO);
        assertThat(listed(model)).containsExactly(first, last);
        assertThat(model.get("resultCount")).isEqualTo(2);
    }

    @Test
    void supportsOpenBoundsAndRejectsReversedPeriod() {
        Owner admin = owner(OwnerRole.SUPER_ADMIN);
        TournamentDto first = event(1, 3, 8, FROM, TournamentType.PADEL_CLINIC, Modalidad.INDIVIDUAL);
        TournamentDto last = event(2, 3, 8, TO, TournamentType.KING_OF_COURT, Modalidad.INDIVIDUAL);
        when(tournaments.getAllTournaments()).thenReturn(List.of(first, last));
        doCallRealMethod().when(access).canManage(any(), any(), any());

        assertThat(listed(list(admin, null, null, TO, null))).containsExactly(last);
        assertThat(listed(list(admin, null, null, null, FROM))).containsExactly(first);
        var invalid = list(admin, null, null, TO, FROM);
        assertThat(invalid.get("invalidPeriod")).isEqualTo(true);
        assertThat(listed(invalid)).isEmpty();
    }

    @Test
    void clubScopeCannotBeExpandedByResetOrDirectQuery() {
        Owner clubAdmin = owner(OwnerRole.CLUB_ADMIN);
        clubAdmin.setClubId(3L);
        TournamentDto own = event(1, 3, 8, FROM, TournamentType.PADEL_CLINIC, Modalidad.INDIVIDUAL);
        when(tournaments.getTournamentsByClub(3L)).thenReturn(List.of(own));
        doCallRealMethod().when(access).canManage(any(), any(), any());

        assertThat(listed(list(clubAdmin, null, null, null, null))).containsExactly(own);
        var foreign = list(clubAdmin, 9L, null, null, null);
        assertThat(listed(foreign)).isEmpty();
        assertThat(foreign.get("filterClubs")).isEqualTo(Map.of(3L, "Club 3"));
        verifyNoInteractions(clubs);
    }

    @Test
    void organizerOnlySeesManagedEventsEvenWithAllClubs() {
        Owner organizer = owner(OwnerRole.ORGANIZER);
        TournamentDto own = event(1, 3, 8, FROM, TournamentType.PADEL_CLINIC, Modalidad.INDIVIDUAL);
        when(tournaments.getAllTournaments()).thenReturn(List.of(own,
                event(2, 9, 11, FROM, TournamentType.PADEL_CLINIC, Modalidad.INDIVIDUAL)));
        doCallRealMethod().when(access).canManage(any(), any(), any());

        assertThat(listed(list(organizer, null, null, null, null))).containsExactly(own);
        assertThat(listed(list(organizer, 9L, null, null, null))).isEmpty();
    }

    private ExtendedModelMap list(Owner owner, Long clubId, AdminEventFormat format, LocalDate from, LocalDate to) {
        var model = new ExtendedModelMap();
        controller.listTournaments(model, owner, clubId, format, from, to);
        return model;
    }

    private static List<TournamentDto> listed(ExtendedModelMap model) {
        return ((List<?>) model.get("tournaments")).stream()
                .map(row -> (TournamentDto) ((Map<?, ?>) row).get("tournament"))
                .toList();
    }

    private static Owner owner(OwnerRole role) {
        Owner owner = new Owner();
        owner.setId(8L);
        owner.setRole(role);
        return owner;
    }

    private static TournamentDto event(long id, long club, long owner, LocalDate date,
                                       TournamentType type, Modalidad modalidad) {
        return TournamentDto.builder().id(id).clubId(club).ownerId(owner).clubNombre("Club " + club)
                .fechaInicio(date).tipo(type).modalidad(modalidad).build();
    }
}
