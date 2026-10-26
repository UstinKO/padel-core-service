package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffAddTeamRequest;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

/**
 * {@code returnTo=payments} переключает редирект и имя flash-атрибута на странице
 * «Оплаты и посещаемость» ({@code successMessage}/{@code errorMessage}, а не {@code success}/
 * {@code error}, которые читает страница Team Playoff) — без этого форма на странице оплат
 * после сохранения уводила бы координатора обратно на страницу Team Playoff.
 */
class TeamPlayoffViewControllerAddTeamRedirectTest {

    @Mock
    private TeamPlayoffService playoffService;
    @Mock
    private TournamentService tournamentService;
    @Mock
    private AmericanoMatchRepository matchRepository;
    @Mock
    private TournamentAccessService tournamentAccessService;
    @Mock
    private PlayerService playerService;

    private TeamPlayoffViewController controller;
    private RedirectAttributes redirectAttributes;

    private static final Long TOURNAMENT_ID = 77L;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new TeamPlayoffViewController(playoffService, tournamentService, matchRepository,
                tournamentAccessService, playerService);
        redirectAttributes = new RedirectAttributesModelMap();
    }

    @Test
    void noReturnTo_redirectsToTeamPlayoffPage_withDefaultFlashKeys() {
        String view = controller.addTeam(TOURNAMENT_ID, new TeamPlayoffAddTeamRequest(), null,
                redirectAttributes, new Owner());

        assertThat(view).isEqualTo("redirect:/tournaments/team-playoff/admin/" + TOURNAMENT_ID);
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("success");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKeys("successMessage", "errorMessage", "error");
    }

    @Test
    void returnToOtherValue_behavesAsNoReturnTo() {
        String view = controller.addTeam(TOURNAMENT_ID, new TeamPlayoffAddTeamRequest(), "team-playoff",
                redirectAttributes, new Owner());

        assertThat(view).isEqualTo("redirect:/tournaments/team-playoff/admin/" + TOURNAMENT_ID);
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("success");
    }

    @Test
    void returnToPayments_success_redirectsToPaymentsPage_withPaymentsFlashKey() {
        String view = controller.addTeam(TOURNAMENT_ID, new TeamPlayoffAddTeamRequest(), "payments",
                redirectAttributes, new Owner());

        assertThat(view).isEqualTo("redirect:/admin/tournaments/" + TOURNAMENT_ID + "/payments");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("successMessage");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKeys("success", "error", "errorMessage");
    }

    @Test
    void returnToPayments_error_redirectsToPaymentsPage_withPaymentsErrorFlashKey() {
        TeamPlayoffAddTeamRequest req = new TeamPlayoffAddTeamRequest();
        doThrow(new InvalidStateException("Este equipo ya fue agregado al torneo"))
                .when(playoffService).addTeam(TOURNAMENT_ID, req);

        String view = controller.addTeam(TOURNAMENT_ID, req, "payments", redirectAttributes, new Owner());

        assertThat(view).isEqualTo("redirect:/admin/tournaments/" + TOURNAMENT_ID + "/payments");
        assertThat(redirectAttributes.getFlashAttributes()).containsKey("errorMessage");
        assertThat(redirectAttributes.getFlashAttributes()).doesNotContainKeys("success", "error", "successMessage");
    }
}
