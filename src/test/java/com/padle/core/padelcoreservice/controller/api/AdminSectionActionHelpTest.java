package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.persistence.tournament.TournamentRegistrationRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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
class AdminSectionActionHelpTest {
    @Autowired MockMvc mvc;
    @Autowired RoundRobinHelpFixtures fixtures;
    @Autowired TournamentRegistrationRepository registrations;

    @ParameterizedTest
    @CsvSource({"ru,Проверить два профиля", "es,Revisar dos perfiles", "en,Review two profiles"})
    void localizedHelpIsIndependentOfFormActions(String locale, String title) throws Exception {
        var owner = fixtures.owner(OwnerRole.SUPER_ADMIN, "Qa-only");
        for (var path : new String[]{"/admin/tournaments?view=calendar", "/admin/players"}) {
            var html = mvc.perform(get(path).param("lang", locale).with(user(owner)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var buttons = Pattern.compile("<button\\b[^>]*data-action-help\\b[^>]*>")
                    .matcher(html).results().map(match -> match.group()).toList();
            assertThat(buttons).isNotEmpty().allSatisfy(button -> assertThat(button)
                    .contains("type=\"button\"", "data-help-title=\"")
                    .doesNotContain("??", "data-help-title=\"\"", "data-help-text=\"\""));
            for (var tag : new String[]{"label", "summary", "a", "button"}) {
                assertThat(Pattern.compile("<" + tag + "\\b[^>]*>(?:(?!</" + tag + ">).)*<button[^>]*data-action-help", Pattern.DOTALL)
                        .matcher(html).find()).as("Подсказка вне %s", tag).isFalse();
            }
            if (path.equals("/admin/players")) assertThat(html).contains("data-help-title=\"" + title + "\"");
        }
    }

    @ParameterizedTest
    @EnumSource(value = OwnerRole.class, names = {"SUPER_ADMIN", "ADMIN", "ORGANIZER", "CLUB_ADMIN"})
    void helpDoesNotExposeUnavailableProfileActionsOrForeignClubPlayers(OwnerRole role) throws Exception {
        var owner = fixtures.owner(role, "Qa-only");
        var event = fixtures.event(owner);
        var foreign = fixtures.event(fixtures.owner(OwnerRole.CLUB_ADMIN, "Qa-only"));
        var calendar = mvc.perform(get("/admin/tournaments").param("view", "calendar").with(user(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(calendar).contains("data-help-topic=\"ac-filters\"", "data-help-topic=\"ac-organization\"");
        var players = mvc.perform(get("/admin/players").with(user(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(players).contains("data-help-topic=\"ap-search\"");
        if (role != OwnerRole.SUPER_ADMIN) assertThat(players).doesNotContain("data-help-topic=\"ap-guest\"",
                "data-help-topic=\"ap-level-inline\"", "data-help-topic=\"ap-credentials\"", "data-help-topic=\"ap-delete\"");
        if (role == OwnerRole.ADMIN || role == OwnerRole.CLUB_ADMIN)
            assertThat(players).doesNotContain("data-help-topic=\"ap-claim-review\"");
        long ownPlayer = registrations.findByTournamentId(event.getId()).get(0).getPlayer().getId();
        var details = mvc.perform(get("/admin/players/" + ownPlayer).with(user(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(details).contains("data-help-topic=\"ap-history\"");
        if (role == OwnerRole.SUPER_ADMIN) assertThat(details).contains("data-help-topic=\"ap-name\"",
                "data-help-topic=\"ap-level\"", "data-help-topic=\"ap-side\"");
        else assertThat(details).doesNotContain("data-help-topic=\"ap-name\"", "data-help-topic=\"ap-level\"",
                "data-help-topic=\"ap-side\"");
        if (role == OwnerRole.CLUB_ADMIN) {
            long foreignPlayer = registrations.findByTournamentId(foreign.getId()).get(0).getPlayer().getId();
            mvc.perform(get("/admin/players/" + foreignPlayer).with(user(owner)))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/error/403"));
            var id = mvc.perform(get("/admin/tournaments/" + foreign.getId() + "/payments").with(user(owner)))
                    .andReturn().getResponse().getStatus();
            assertThat(id).isIn(302, 403);
        }
    }
}
