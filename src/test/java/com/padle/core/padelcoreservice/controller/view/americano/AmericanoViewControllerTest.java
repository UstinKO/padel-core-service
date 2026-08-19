package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ui.Model;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * LFPT-314: юнит-тест на GET /tournaments/americano/admin/{tournamentId}/preview.
 * До фикса неинициализированная ветка возвращала несуществующий шаблон
 * admin/americano/initialize (TemplateInputException в реальном рендеринге),
 * а инициализированная ветка редиректила на несуществующий /admin/tournaments/americano/{id}.
 */
class AmericanoViewControllerTest {

    @Mock
    private AmericanoService americanoService;

    @Mock
    private TournamentService tournamentService;

    private AmericanoViewController controller;

    private Model model;
    private RedirectAttributes redirectAttributes;

    private static final Long TOURNAMENT_ID = 42L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new AmericanoViewController(americanoService, tournamentService);
        model = new ExtendedModelMap();
        redirectAttributes = new RedirectAttributesModelMap();
    }

    private TournamentDto tournamentDto(TournamentType tipo) {
        return TournamentDto.builder()
                .id(TOURNAMENT_ID)
                .tipo(tipo)
                .estado(TournamentStatus.CERRADO)
                .build();
    }

    @Test
    void notInitialized_redirectsToTournamentDetailsWithoutNewFlash() {
        when(tournamentService.getActiveTournamentById(TOURNAMENT_ID))
                .thenReturn(Optional.of(tournamentDto(TournamentType.AMERICANO)));
        when(americanoService.isInitialized(TOURNAMENT_ID)).thenReturn(false);

        String view = controller.showAdminPreviewForm(TOURNAMENT_ID, model, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/admin/tournaments/" + TOURNAMENT_ID);
        assertThat(redirectAttributes.getFlashAttributes()).isEmpty();
    }

    @Test
    void alreadyInitialized_redirectsToWorkingAdminTournamentRoute() {
        when(tournamentService.getActiveTournamentById(TOURNAMENT_ID))
                .thenReturn(Optional.of(tournamentDto(TournamentType.AMERICANO)));
        when(americanoService.isInitialized(TOURNAMENT_ID)).thenReturn(true);

        String view = controller.showAdminPreviewForm(TOURNAMENT_ID, model, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/tournaments/americano/admin/" + TOURNAMENT_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("info"))
                .isEqualTo("El torneo ya está inicializado");
    }

    @Test
    void wrongTournamentType_regressionUnchanged() {
        when(tournamentService.getActiveTournamentById(TOURNAMENT_ID))
                .thenReturn(Optional.of(tournamentDto(TournamentType.KING_OF_COURT)));

        String view = controller.showAdminPreviewForm(TOURNAMENT_ID, model, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/admin/tournaments/" + TOURNAMENT_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("error"))
                .isEqualTo("Este torneo no es de tipo Americano");
    }
}
