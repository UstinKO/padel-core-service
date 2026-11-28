package com.padle.core.padelcoreservice.config;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.persistence.tournament.Tournament;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

class AnalyticsTrafficConfigTest {
    private final AnalyticsTrafficConfig config = new AnalyticsTrafficConfig(
            new MockEnvironment().withProperty("spring.profiles.active", "prod"));

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ordinaryPlayerAndAnonymousAreVisitors() {
        assertThat(config.classify(null, "/", false).group()).isEqualTo("visitor");
        assertThat(config.classify(auth("ROLE_PLAYER"), "/players/dashboard", false).group())
                .isEqualTo("visitor");
        assertThat(config.classify(auth("ROLE_ANONYMOUS"), "/login", false).group()).isEqualTo("visitor");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_SUPER_ADMIN", "ROLE_ADMIN", "ROLE_ORGANIZER", "ROLE_CLUB_ADMIN"})
    void staffIsInternalEvenOnPublicPage(String role) {
        assertThat(config.classify(auth(role), "/torneo/123", false))
                .isEqualTo(new AnalyticsTrafficConfig.TrafficContext("internal", "staff_account"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/tournaments/123/payments", "/tournaments/team-playoff/admin/123"})
    void administrationPathsAreInternal(String path) {
        assertThat(config.classify(null, path, false).group()).isEqualTo("internal");
    }

    @Test
    void testFlagOverridesStaffAndDoesNotDependOnTournamentNameOrVisibility() {
        assertThat(config.classify(auth("ROLE_ADMIN"), "/torneo/123", true))
                .isEqualTo(new AnalyticsTrafficConfig.TrafficContext("test", "test_event"));
        for (Object tournament : new Object[]{TournamentDto.builder().testTournament(true).build(),
                Tournament.builder().testTournament(true).build()}) {
            var view = new ModelAndView("page", "tournament", tournament);
            config.postHandle(new MockHttpServletRequest("GET", "/torneo/123"),
                    new MockHttpServletResponse(), new Object(), view);
            assertThat(view.getModel().get("analyticsTraffic"))
                    .isEqualTo(new AnalyticsTrafficConfig.TrafficContext("test", "test_event"));
        }
    }

    @Test
    void kingUsesExplicitParentFlagWithoutLookingUpTheUnrelatedKingId() {
        var view = new ModelAndView("king-of-court-view", "analyticsTestTournament", true);
        config.postHandle(new MockHttpServletRequest("GET", "/tournaments/king-of-court/7"),
                new MockHttpServletResponse(), new Object(), view);
        assertThat(view.getModel().get("analyticsTraffic"))
                .isEqualTo(new AnalyticsTrafficConfig.TrafficContext("test", "test_event"));
    }

    @Test
    void nonProductionAndPreviewAreTestEvenWithExplicitAnalyticsId() {
        var local = new AnalyticsTrafficConfig(new MockEnvironment());
        assertThat(local.classify(null, "/", false).group()).isEqualTo("test");
        assertThat(config.classify(auth("ROLE_SUPER_ADMIN"), "/preview/players", false).group()).isEqualTo("test");
    }

    @Test
    void redirectAndRestResponsesAreNotModified() {
        var view = new ModelAndView("redirect:/");
        config.postHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), view);
        assertThat(view.getModel()).isEmpty();
        var redirect = new ModelAndView(new RedirectView("/target"));
        config.postHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), redirect);
        assertThat(redirect.getModel()).isEmpty();
        config.postHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);
    }

    @Test
    void mvcCarriesAuthenticatedContextWithoutChangingApiOrRedirectUrls() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FixtureController()).addInterceptors(config).build();
        SecurityContextHolder.getContext().setAuthentication(auth("ROLE_CLUB_ADMIN"));
        mvc.perform(get("/page")).andExpect(model().attribute("analyticsTraffic",
                new AnalyticsTrafficConfig.TrafficContext("internal", "staff_account")));
        mvc.perform(get("/redirect")).andExpect(redirectedUrl("/target?source=qa"));
        mvc.perform(get("/api")).andExpect(content().string("ok"));
    }

    @Controller
    static class FixtureController {
        @GetMapping("/page")
        String page() { return "fixture"; }

        @GetMapping("/redirect")
        ModelAndView redirect() { return new ModelAndView(new RedirectView("/target?source=qa")); }

        @GetMapping("/api")
        @ResponseBody
        String api() { return "ok"; }
    }

    private static UsernamePasswordAuthenticationToken auth(String role) {
        return UsernamePasswordAuthenticationToken.authenticated("synthetic", "", AuthorityUtils.createAuthorityList(role));
    }
}
