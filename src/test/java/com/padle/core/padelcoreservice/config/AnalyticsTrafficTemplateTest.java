package com.padle.core.padelcoreservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsTrafficTemplateTest {
    @Test
    void rendersActualTagWithContextBeforeTheOnlyConfigAndCreatesBrowserFixtures() throws Exception {
        var cases = Map.of("visitor", "public", "internal", "staff_account", "test", "test_event");
        for (var entry : cases.entrySet()) {
            String html = render(true, new AnalyticsTrafficConfig.TrafficContext(entry.getKey(), entry.getValue()));
            assertThat(html).contains("/js/analytics-traffic.js", "gtag('set', traffic)", entry.getValue());
            assertThat(html.indexOf("gtag('set', traffic)")).isLessThan(html.indexOf("gtag('config'"));
            assertThat(html.split("gtag\\('config'", -1)).hasSize(2);
            write(entry.getKey(), html);
        }
        String missing = render(true, null);
        assertThat(missing).contains("unclassified", "missing_context");
        write("missing", missing);
        String disabled = render(false, null);
        assertThat(disabled).doesNotContain("googletagmanager.com", "gtag(", "analytics-traffic.js");
        write("disabled", disabled);
        if (Boolean.getBoolean("browser.qa")) {
            var directory = Path.of("target/analytics-traffic");
            var log = directory.resolve("browser.log");
            var process = new ProcessBuilder("node", "--test", "tests/analytics-traffic.browser.mjs")
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
            boolean completed = process.waitFor(120, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).as("Браузерная проверка завершилась вовремя").isTrue();
            assertThat(process.exitValue()).as(Files.readString(log)).isZero();
        }
    }

    private static void write(String name, String html) throws Exception {
        var directory = Path.of("target/analytics-traffic");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".html"), html);
    }

    private static String render(boolean enabled, AnalyticsTrafficConfig.TrafficContext traffic) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var servlet = new MockServletContext();
        try (var application = new StaticWebApplicationContext()) {
            application.setServletContext(servlet);
            application.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fixture",
                    Map.of("app.analytics.ga4-id", enabled ? "G-TEST000000" : "")));
            application.refresh();
            var exchange = JakartaServletWebApplication.buildApplication(servlet)
                    .buildExchange(new MockHttpServletRequest(servlet), new MockHttpServletResponse());
            var context = new WebContext(exchange, Locale.ENGLISH);
            context.setVariable("analyticsTraffic", traffic);
            context.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                    new ThymeleafEvaluationContext(application, new DefaultConversionService()));
            return engine.process("fragments/analytics", Set.of("ga4"), context);
        }
    }
}
