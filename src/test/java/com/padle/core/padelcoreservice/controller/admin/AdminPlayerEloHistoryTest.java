package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.preview.PreviewEloRanking;
import com.padle.core.padelcoreservice.rating.PilotEloRanking;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.service.TournamentService;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminPlayerEloHistoryTest {
    @Test
    void onlySuperAdminReceivesFullEloHistory() {
        var players = mock(PlayerService.class);
        var tournaments = mock(TournamentService.class);
        var registrations = mock(TournamentRegistrationRepository.class);
        var rating = mock(PilotEloRanking.class);
        var player = PlayerResponseDto.builder().id(461L).email("player@example.com").build();
        var row = mock(PreviewEloRanking.Row.class);
        when(players.obtenerJugadorPorId(461L)).thenReturn(player);
        when(tournaments.getActiveRegistrationsByPlayer(461L)).thenReturn(List.of());
        when(rating.playerRow(461L)).thenReturn(Optional.of(row));
        var controller = new AdminPlayerController(players, tournaments, registrations, mock(MessageSource.class), Optional.of(rating));
        var superAdmin = mock(Owner.class);
        when(superAdmin.isSuperAdmin()).thenReturn(true);
        var rootModel = new ExtendedModelMap();
        controller.viewPlayer(461L, rootModel, superAdmin);
        assertThat(rootModel.get("adminEloRow")).isSameAs(row);
        assertThat(rootModel.get("eloHistoryAvailable")).isEqualTo(true);
        var clubAdmin = mock(Owner.class);
        when(clubAdmin.isClubAdmin()).thenReturn(true);
        when(clubAdmin.getId()).thenReturn(7L);
        when(registrations.existsByTournamentOwnerIdAndPlayerId(7L, 461L)).thenReturn(true);
        when(tournaments.getTournamentsForOwner(7L, false)).thenReturn(List.of());
        var clubModel = new ExtendedModelMap();
        controller.viewPlayer(461L, clubModel, clubAdmin);
        assertThat(clubModel).doesNotContainKeys("adminEloRow", "eloHistoryAvailable");
        verify(rating, times(1)).playerRow(461L);
    }
}
