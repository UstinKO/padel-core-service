package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest(properties = {"spring.mail.username=test@example.test", "spring.mail.password=test",
        "spring.security.oauth2.client.registration.google.client-id=test", "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test", "app.scheduling.enabled=false", "telegram.enabled=false",
        "telegram.players.enabled=false", "telegram.players.automatic-enabled=false", "app.analytics.meta.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class RoundRobinActionHelpTest {
    private final MockMvc mvc;
    private final RoundRobinHelpFixtures fixtures;

    @Autowired
    RoundRobinActionHelpTest(MockMvc mvc, RoundRobinHelpFixtures fixtures) {
        this.mvc = mvc;
        this.fixtures = fixtures;
    }

    @ParameterizedTest
    @CsvSource({"POINTS,ru", "POINTS,es", "POINTS,en", "SETS,ru", "SETS,es", "SETS,en"})
    void helpUsesActualScoringModeAndDoesNotBecomeAFormAction(RoundRobinScoringMode mode, String locale) throws Exception {
        var owner = fixtures.owner(OwnerRole.SUPER_ADMIN, "Qa-only-1512");
        var event = fixtures.event(owner);
        fixtures.initialize(event, mode);
        var html = mvc.perform(get("/tournaments/team-americano/admin/" + event.getId()).with(user(owner)).param("lang", locale))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var suffix = mode == RoundRobinScoringMode.SETS ? "sets" : "points";
        assertThat(count(html, "data-help-topic=\"rr-table-" + suffix + "\"")).isEqualTo(1);
        assertThat(count(html, "data-help-topic=\"rr-score-" + suffix + "\"")).isEqualTo(10);
        assertThat(count(html, "data-help-topic=\"rr-rest\"")).isEqualTo(5);
        assertThat(count(html, "<form[^>]*data-rr-score\\b")).isEqualTo(10);
        assertThat(Pattern.compile("<form\\b[^>]*data-rr-score\\b[^>]*>(.*?)</form>", Pattern.DOTALL)
                .matcher(html).results().toList()).allSatisfy(form ->
                assertThat(count(form.group(1), "<button[^>]*type=\"submit\"")).isEqualTo(1));
        var buttons = Pattern.compile("<button\\b[^>]*data-action-help\\b[^>]*>").matcher(html).results()
                .map(MatchResult::group).toList();
        assertThat(buttons).isNotEmpty().allSatisfy(button -> {
            assertThat(button).contains("type=\"button\"", "data-help-title=\"", "data-help-text=\"")
                    .doesNotContain("??", "data-help-title=\"\"", "data-help-text=\"\"");
        });
        assertThat(buttons.stream().filter(button -> button.contains("rr-score-")).toList())
                .allMatch(button -> mode != RoundRobinScoringMode.SETS || button.contains("6:5"));
        assertThat(html).doesNotContain("data-help-topic=\"early\"", "data-help-topic=\"playoff\"", "data-help-topic=\"start\"");
        for (var tag : new String[]{"label", "summary", "a", "button"}) {
            assertThat(count(html, "<" + tag + "\\b[^>]*>(?:(?!</" + tag + ">).)*<button[^>]*data-action-help"))
                    .as("Подсказка не вложена в %s", tag).isZero();
        }
    }

    @ParameterizedTest
    @EnumSource(value = OwnerRole.class, names = {"SUPER_ADMIN", "ADMIN", "ORGANIZER", "CLUB_ADMIN"})
    void existingRolesGetHelpOnlyWithinTheirTournament(OwnerRole role) throws Exception {
        var owner = fixtures.owner(role, "Qa-only-1512");
        var event = fixtures.event(owner);
        var html = mvc.perform(get("/admin/tournaments/" + event.getId()).with(user(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(count(html, "data-help-topic=\"rr-start\"")).isEqualTo(1);
        assertThat(count(html, "data-help-topic=\"rr-mode\"")).isEqualTo(1);
        var other = fixtures.event(fixtures.owner(OwnerRole.CLUB_ADMIN, "Qa-only-1512"));
        if (role == OwnerRole.ORGANIZER || role == OwnerRole.CLUB_ADMIN) {
            mvc.perform(get("/tournaments/team-americano/admin/" + other.getId()).with(user(owner)))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/error/403"));
        } else {
            mvc.perform(get("/tournaments/team-americano/admin/" + other.getId()).with(user(owner)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void publicResultsNeverGetAdministrativeHelp() throws Exception {
        var owner = fixtures.owner(OwnerRole.SUPER_ADMIN, "Qa-only-1512");
        var event = fixtures.event(owner);
        fixtures.initialize(event, RoundRobinScoringMode.SETS);
        var html = mvc.perform(get("/tournaments/team-americano/" + event.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("data-action-help", "data-rr-score", "data-rr-add");
    }

    @ParameterizedTest
    @EnumSource(RoundRobinScoringMode.class)
    void additionalMatchesAndFinalPositionsHaveContextualHelp(RoundRobinScoringMode mode) throws Exception {
        var owner = fixtures.owner(OwnerRole.SUPER_ADMIN, "Qa-only-1512");
        var event = fixtures.event(owner);
        fixtures.initialize(event, mode);
        fixtures.scoreAll(event, mode);
        var url = "/tournaments/team-americano/admin/" + event.getId();
        var afterCircle = mvc.perform(get(url).with(user(owner))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(afterCircle).contains("data-help-topic=\"rr-additional\"", "data-help-topic=\"rr-placement\"",
                "data-help-topic=\"rr-finish\"");
        assertThat(count(afterCircle, "<summary\\b[^>]*>(?:(?!</summary>).)*data-action-help")).isZero();
        fixtures.finalAndBronze(event);
        fixtures.scoreAll(event, mode);
        fixtures.finish(event);
        var finished = mvc.perform(get(url).with(user(owner))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(finished).contains("data-help-topic=\"rr-final\"").doesNotContain("data-rr-add");
    }

    private long count(String html, String expression) {
        return Pattern.compile(expression, Pattern.DOTALL).matcher(html).results().count();
    }
}
