package com.padle.core.padelcoreservice.config;

import com.padle.core.padelcoreservice.controller.view.KingOfCourtViewController;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ui.ExtendedModelMap;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsKingTrafficTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bothPublicRoutesCarryTheParentTournamentFlag(boolean test) {
        var parent = Tournament.builder().id(999L).testTournament(test).build();
        var king = new TournamentKingOfCourt();
        king.setId(7L);
        king.setTournament(parent);
        var repository = mock(TournamentKingOfCourtRepository.class);
        when(repository.findById(7L)).thenReturn(Optional.of(king));
        when(repository.findAllByTournamentIdAndIsActiveTrue(999L)).thenReturn(List.of(king));
        var controller = new KingOfCourtViewController(repository, mock(KingOfCourtService.class));
        var byKing = new ExtendedModelMap();
        var byParent = new ExtendedModelMap();
        controller.viewKingOfCourtByKingId(7L, byKing, null);
        controller.viewKingOfCourt(999L, byParent, null);
        assertThat(byKing.get("analyticsTestTournament")).isEqualTo(test);
        assertThat(byParent.get("analyticsTestTournament")).isEqualTo(test);
        assertThat(byKing.get("tournamentId")).isEqualTo(999L);
    }
}
