package com.padle.core.padelcoreservice.controller.admin;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.messageresolver.StandardMessageResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class TournamentSocialDraftTemplateTest {

    @Test
    void rendersLocalizedPanelWithTournamentEndpoint() throws Exception {
        var properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/i18n/social-draft_ru.properties")) {
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

        var servletContext = new MockServletContext();
        var request = new MockHttpServletRequest(servletContext);
        request.setContextPath("/padel");
        var exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(request, new MockHttpServletResponse());
        var html = engine.process("fragments/tournament-social-draft",
                new WebContext(exchange, Locale.forLanguageTag("ru"), Map.of("tournamentId", 151L)));

        assertThat(html).contains("data-endpoint=\"/padel/admin/tournaments/151/social-draft\"",
                "Подготовить публикации", "Скачать комплект ZIP", "id=\"social-bracket\"",
                "data-msg-incomplete=\"Проверьте");
    }
}
