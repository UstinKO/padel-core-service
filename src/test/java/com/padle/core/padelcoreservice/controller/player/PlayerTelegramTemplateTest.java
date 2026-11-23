package com.padle.core.padelcoreservice.controller.player;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.messageresolver.StandardMessageResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class PlayerTelegramTemplateTest {
    @ParameterizedTest
    @CsvSource({"ru, Подключить Telegram", "es, Conectar Telegram", "en, Connect Telegram"})
    void rendersConnectionControlsInAllLanguagesAndHonorsTheFeatureFlag(String language, String label) throws Exception {
        String rendered = render(language, true);
        assertThat(rendered).contains(label, "data-tg-connect", "data-tg-disconnect", "rel=\"noopener noreferrer\"");
        assertThat(rendered).doesNotContain("??telegram", "th:attr=");
        assertThat(render(language, false)).doesNotContain("data-tg-connect");
        Files.createDirectories(Path.of("target/telegram-ui"));
        Files.writeString(Path.of("target/telegram-ui/profile-" + language + ".html"), rendered);
    }
    private String render(String language, boolean enabled) throws Exception {
        var properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/i18n/messages_" + language + ".properties")) {
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        var messages = new StandardMessageResolver(); messages.setDefaultMessages(properties);
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver); engine.setMessageResolver(messages);
        var servlet = new MockServletContext();
        try (var application = new StaticWebApplicationContext()) {
            application.setServletContext(servlet);
            application.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture",
                    Map.of("telegram.players.enabled", String.valueOf(enabled))));
            application.refresh();
            var exchange = JakartaServletWebApplication.buildApplication(servlet)
                    .buildExchange(new MockHttpServletRequest(servlet), new MockHttpServletResponse());
            var context = new WebContext(exchange, Locale.forLanguageTag(language));
            context.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                    new ThymeleafEvaluationContext(application, new DefaultConversionService()));
            return engine.process("fragments/player-telegram", Set.of("profile"), context);
        }
    }
}
