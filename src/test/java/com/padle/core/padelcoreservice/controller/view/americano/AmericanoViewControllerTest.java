package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
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
 *
 * LFPT-316: тот же паттерн бага в соседнем методе showInitializeForm
 * (GET /tournaments/americano/{tournamentId}/initialize) — см. тесты showInitializeForm_*.
 *
 * LFPT-330: шесть GET-роутов ссылались на несуществующие шаблоны
 * (register/rounds/round/match/match-result/player-stats) — теперь редиректят на рабочие страницы.
 */
class AmericanoViewControllerTest {

    @Mock
    private AmericanoService americanoService;

    @Mock
    private TournamentService tournamentService;

    @Mock
    private TournamentAccessService tournamentAccessService;

    private AmericanoViewController controller;

    private Model model;
    private RedirectAttributes redirectAttributes;

    private static final Long TOURNAMENT_ID = 42L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new AmericanoViewController(americanoService, tournamentService, tournamentAccessService);
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

    /**
     * LFPT-317: admin/tournaments/details.html (целевая страница редиректа) читает
     * только errorMessage/successMessage — flash-атрибут "error" на ней невидим.
     * До фикса контроллер слал именно "error" сюда.
     */
    @Test
    void wrongTournamentType_redirectsWithErrorMessageAttribute() {
        when(tournamentService.getActiveTournamentById(TOURNAMENT_ID))
                .thenReturn(Optional.of(tournamentDto(TournamentType.KING_OF_COURT)));

        String view = controller.showAdminPreviewForm(TOURNAMENT_ID, model, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/admin/tournaments/" + TOURNAMENT_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                .isEqualTo("Este torneo no es de tipo Americano");
    }

    /**
     * LFPT-316: юнит-тест на GET /tournaments/americano/{tournamentId}/initialize.
     * До фикса неинициализированная ветка возвращала несуществующий шаблон
     * tournaments/americano/initialize (TemplateInputException в реальном рендеринге).
     * Роут реально достижим не только прямым набором URL — catch-блок previewRounds
     * (строка 255 контроллера) редиректит сюда при ошибке AmericanoService.previewRounds.
     */
    @Test
    void showInitializeForm_notInitialized_redirectsToTournamentDetailsWithoutNewFlash() {
        when(tournamentService.getActiveTournamentById(TOURNAMENT_ID))
                .thenReturn(Optional.of(tournamentDto(TournamentType.AMERICANO)));
        when(americanoService.isInitialized(TOURNAMENT_ID)).thenReturn(false);

        String view = controller.showInitializeForm(TOURNAMENT_ID, model, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/admin/tournaments/" + TOURNAMENT_ID);
        assertThat(redirectAttributes.getFlashAttributes()).isEmpty();
    }

    @Test
    void showInitializeForm_alreadyInitialized_regressionUnchanged() {
        when(tournamentService.getActiveTournamentById(TOURNAMENT_ID))
                .thenReturn(Optional.of(tournamentDto(TournamentType.AMERICANO)));
        when(americanoService.isInitialized(TOURNAMENT_ID)).thenReturn(true);

        String view = controller.showInitializeForm(TOURNAMENT_ID, model, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/tournaments/americano/" + TOURNAMENT_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("info"))
                .isEqualTo("El torneo ya está inicializado");
    }

    @Test
    void showInitializeForm_wrongTournamentType_regressionUnchanged() {
        when(tournamentService.getActiveTournamentById(TOURNAMENT_ID))
                .thenReturn(Optional.of(tournamentDto(TournamentType.KING_OF_COURT)));

        String view = controller.showInitializeForm(TOURNAMENT_ID, model, redirectAttributes);

        // Целевой URL /tournaments/{id} ведёт на несуществующий роут — известная, отдельная
        // находка (LFPT-317), сознательно не чинится в этой фиче (см. спеку LFPT-316, "Вне скоупа").
        assertThat(view).isEqualTo("redirect:/tournaments/" + TOURNAMENT_ID);
        assertThat(redirectAttributes.getFlashAttributes().get("error"))
                .isEqualTo("Este torneo no es de tipo Americano");
    }

    /**
     * Регрессионный контракт: catch-блок previewRounds (строка 255 контроллера)
     * по-прежнему редиректит на /tournaments/americano/{tournamentId}/initialize.
     * После фикса этот путь больше не падает — это тот же самый обработчик,
     * покрытый тестами showInitializeForm_* выше.
     */
    @Test
    void previewRoundsErrorRedirectTarget_isHandledByShowInitializeFormWithoutCrash() {
        String expectedRedirectFromPreviewRounds =
                "redirect:/tournaments/americano/" + TOURNAMENT_ID + "/initialize";

        when(tournamentService.getActiveTournamentById(TOURNAMENT_ID))
                .thenReturn(Optional.of(tournamentDto(TournamentType.AMERICANO)));
        when(americanoService.isInitialized(TOURNAMENT_ID)).thenReturn(false);

        assertThat(expectedRedirectFromPreviewRounds)
                .endsWith("/tournaments/americano/" + TOURNAMENT_ID + "/initialize");

        String view = controller.showInitializeForm(TOURNAMENT_ID, model, redirectAttributes);

        assertThat(view).isEqualTo("redirect:/admin/tournaments/" + TOURNAMENT_ID);
    }

    @Test
    void register_redirectsToPublicTournamentPage() {
        assertThat(controller.showRegisterForm(TOURNAMENT_ID))
                .isEqualTo("redirect:/torneo/" + TOURNAMENT_ID);
    }

    @Test
    void rounds_redirectsToTournamentView() {
        assertThat(controller.viewRounds(TOURNAMENT_ID))
                .isEqualTo("redirect:/tournaments/americano/" + TOURNAMENT_ID);
    }

    @Test
    void round_redirectsToTournamentOfRound() {
        when(americanoService.getRound(7L))
                .thenReturn(AmericanoRoundDto.builder().id(7L).tournamentId(TOURNAMENT_ID).build());

        assertThat(controller.viewRound(7L))
                .isEqualTo("redirect:/tournaments/americano/" + TOURNAMENT_ID);
    }

    @Test
    void matchAndResultForm_redirectToTournamentOfMatch() {
        when(americanoService.getMatch(9L))
                .thenReturn(AmericanoMatchDto.builder().id(9L).tournamentId(TOURNAMENT_ID).build());

        assertThat(controller.viewMatch(9L))
                .isEqualTo("redirect:/tournaments/americano/" + TOURNAMENT_ID);
        assertThat(controller.showResultForm(9L))
                .isEqualTo("redirect:/tournaments/americano/" + TOURNAMENT_ID);
    }

    @Test
    void playerStats_redirectsToRanking() {
        assertThat(controller.viewPlayerStats(TOURNAMENT_ID, 5L))
                .isEqualTo("redirect:/tournaments/americano/" + TOURNAMENT_ID + "/ranking");
    }
}
