package com.padle.core.padelcoreservice.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-0618: ссылки на страницы сайта в email-письмах должны формироваться из {@code app.base-url}
 * (см. {@code EmailService.setLegalFooterLinks} и переменные вида {@code tournamentUrl}/{@code eventUrl}),
 * а не хардкодиться прямо в шаблоне — иначе при смене домена нужно вручную править каждое письмо.
 * Чистый unit-тест без Spring-контекста — читает .html-файлы с диска.
 */
class EmailTemplateLinksTest {

    private static final Path TEMPLATES_DIR = Path.of("src/main/resources/templates/email");
    private static final Pattern HREF_ATTRIBUTE = Pattern.compile("href\\s*=\\s*\"([^\"]*)\"");
    // единственные домены, которые можно прописывать в шаблоне напрямую — соцсети, не наш сайт
    private static final Set<String> ALLOWED_LITERAL_HOSTS = Set.of("instagram.com", "facebook.com", "twitter.com");

    @Test
    void emailTemplates_noReferenceToOldDomain() throws IOException {
        for (Path template : emailTemplates()) {
            String content = Files.readString(template).toLowerCase();
            assertThat(content)
                    .as("шаблон %s не должен ссылаться на старый домен epadel.org", template.getFileName())
                    .doesNotContain("epadel.org");
        }
    }

    @Test
    void emailTemplates_pageLinksAreBuiltFromBaseUrlNotHardcoded() throws IOException {
        for (Path template : emailTemplates()) {
            String content = Files.readString(template);
            Matcher matcher = HREF_ATTRIBUTE.matcher(content);

            while (matcher.find()) {
                String href = matcher.group(1);
                if (href.startsWith("mailto:") || href.contains("${")) {
                    continue; // mailto и динамические th:href="${...}" — не хардкод домена
                }
                if (!href.startsWith("http://") && !href.startsWith("https://")) {
                    continue; // относительные/якорные ссылки
                }

                String host = URI.create(href).getHost();
                boolean allowed = host != null && ALLOWED_LITERAL_HOSTS.stream().anyMatch(host::endsWith);

                assertThat(allowed)
                        .as("ссылка %s в %s должна формироваться из ${baseUrl}, а не хардкодиться в шаблоне",
                                href, template.getFileName())
                        .isTrue();
            }
        }
    }

    private static List<Path> emailTemplates() throws IOException {
        try (Stream<Path> files = Files.list(TEMPLATES_DIR)) {
            return files.filter(p -> p.toString().endsWith(".html")).toList();
        }
    }
}
