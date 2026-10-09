package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import com.padle.core.padelcoreservice.service.americano.TeamAmericanoService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LFPT-391: страницы управления запущенным турниром Americano / Team Americano / Team Playoff
 * лежат не под /admin/** и не имели ролевого гейта — любой залогиненный игрок открывал их,
 * зная id турнира. Проверка через реальную цепочку фильтров и method security (MockMvc),
 * сервисы замоканы.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key",
})
class AmericanoAdminPagesSecurityTest {

    private static final long TOURNAMENT_ID = 42L;

    /** Страницы с проверкой клубной изоляции (assertCanManageTournament). */
    private static final List<String> MANAGED_PAGES = List.of(
            "/tournaments/americano/admin/" + TOURNAMENT_ID,
            "/tournaments/team-americano/admin/" + TOURNAMENT_ID,
            "/tournaments/team-playoff/admin/" + TOURNAMENT_ID);

    private static final String PREVIEW_PAGE = "/tournaments/americano/admin/" + TOURNAMENT_ID + "/preview";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private AmericanoService americanoService;
    @MockitoBean private TeamAmericanoService teamAmericanoService;
    @MockitoBean private TeamPlayoffService teamPlayoffService;
    @MockitoBean private TournamentService tournamentService;
    @MockitoBean private TournamentAccessService tournamentAccessService;

    @BeforeEach
    void stopRightAfterAccessCheck() {
        // Дальше гейта и клубной проверки контроллер не идёт: нас интересует только доступ
        when(tournamentService.getActiveTournamentById(anyLong()))
                .thenThrow(new IllegalStateException("гейт пройден"));
    }

    @Test
    void jugador_recibe403_yNoSeTocanLosServicios() throws Exception {
        for (String url : allPages()) {
            mockMvc.perform(get(url).with(user("player@test").roles("PLAYER")))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/error/403"));
        }

        verifyNoInteractions(tournamentService, tournamentAccessService,
                americanoService, teamAmericanoService, teamPlayoffService);
    }

    @Test
    void anonimo_vaAlLogin() throws Exception {
        for (String url : allPages()) {
            mockMvc.perform(get(url))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
    }

    @ParameterizedTest
    @EnumSource(OwnerRole.class)
    void owner_pasaElGate_yLaVerificacionDeClubEsIncondicional(OwnerRole role) {
        Owner owner = Owner.builder().id(7L).email(role + "@test").password("x")
                .firstName("Test").lastName("Owner").role(role).isActive(true).build();

        for (String url : MANAGED_PAGES) {
            assertThatThrownBy(() -> mockMvc.perform(get(url).with(user(owner))))
                    .hasRootCauseMessage("гейт пройден");
        }

        verify(tournamentAccessService, times(MANAGED_PAGES.size()))
                .assertCanManageTournament(owner, TOURNAMENT_ID);
    }

    private static List<String> allPages() {
        return Stream.concat(MANAGED_PAGES.stream(), Stream.of(PREVIEW_PAGE)).toList();
    }
}
