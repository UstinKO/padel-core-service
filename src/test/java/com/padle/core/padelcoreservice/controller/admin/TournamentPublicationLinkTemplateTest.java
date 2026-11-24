package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.model.enums.EventFormat;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.messageresolver.StandardMessageResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TournamentPublicationLinkTemplateTest {
    @ParameterizedTest
    @CsvSource({"ru, Ссылка для публикации", "es, Enlace para publicar", "en, Publication link"})
    void rendersLocalizedPublicLinkWithoutQueryOrPersonalData(String language, String label) throws Exception {
        String html = render(language, TournamentStatus.REGISTRO_ABIERTO, true, true);
        assertThat(html).contains(label, "data-publication-id=\"185\"",
                "data-publication-time=\"13:30\"", "data-site-base-url=\"https://configured.example/padel\"",
                "data-publication-channel=\"telegram\"", "data-publication-channel=\"stories\"");
        assertThat(html).doesNotContain("??", "utm_campaign=admin", "playerEmail");
    }

    @ParameterizedTest
    @EnumSource(value = TournamentStatus.class, names = {"BORRADOR", "CANCELADO"})
    void doesNotOfferPublicationForDraftOrCancelled(TournamentStatus status) throws Exception {
        assertThat(render("ru", status, true, true)).doesNotContain("data-publication-id=");
    }

    @ParameterizedTest
    @CsvSource({"false, true", "true, false"})
    void doesNotOfferPublicationWithoutViewAccessOrForDeletedEvent(boolean canView, boolean active) throws Exception {
        assertThat(render("ru", TournamentStatus.REGISTRO_ABIERTO, active, canView))
                .doesNotContain("data-publication-id=");
    }

    @ParameterizedTest
    @EnumSource(value = TournamentStatus.class, names = {"PUBLICADO", "CERRADO", "FINALIZADO"})
    void keepsPublicCardAvailableForPublishedStatuses(TournamentStatus status) throws Exception {
        assertThat(render("ru", status, true, true)).contains("data-publication-id=\"185\"");
    }

    @Test
    void insertsOnlyOneTriggerWithoutDialogControlsIntoEachListRow() throws Exception {
        assertThat(render("ru", TournamentStatus.REGISTRO_ABIERTO, true, true, true))
                .contains("data-publication-id=\"185\"")
                .doesNotContain("data-publication-close", "data-publication-channel", "<dialog");
    }

    private String render(String language, TournamentStatus status, boolean active, boolean canView) throws Exception {
        return render(language, status, active, canView, false);
    }

    private String render(String language, TournamentStatus status, boolean active, boolean canView, boolean buttonOnly) throws Exception {
        var properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/i18n/messages_" + language + ".properties")) {
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        var messages = new StandardMessageResolver();
        messages.setDefaultMessages(properties);
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setMessageResolver(messages);
        var context = new MockServletContext();
        var request = new MockHttpServletRequest(context);
        request.setRequestURI("/admin/tournaments");
        request.setQueryString("utm_campaign=admin&playerEmail=private");
        var exchange = JakartaServletWebApplication.buildApplication(context)
                .buildExchange(request, new MockHttpServletResponse());
        var tournament = new TournamentDto();
        tournament.setId(185L);
        tournament.setClubNombre("Arena <test> & Padel");
        tournament.setFechaInicio(LocalDate.of(2026, 11, 25));
        tournament.setHoraInicio(LocalTime.of(13, 30));
        tournament.setCategoriaNivel("TODOS");
        tournament.setEstado(status);
        tournament.setIsActive(active);
        var webContext = new WebContext(exchange,
                Locale.forLanguageTag(language), Map.of(
                "siteBaseUrl", "https://configured.example/padel", "levelLabel", "TODOS",
                "item", Map.of("tournament", tournament, "canView", canView, "format", EventFormat.PADEL_CLINIC)));
        return buttonOnly
                ? engine.process("fragments/tournament-publication-link", Set.of("publicationButton"), webContext)
                : engine.process("fragments/tournament-publication-link", webContext);
    }
}
