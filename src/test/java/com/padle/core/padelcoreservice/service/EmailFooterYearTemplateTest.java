package com.padle.core.padelcoreservice.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-0657: подвал писем выводит год через {@code #{email.footer.rights(${year})}},
 * а {@code EmailService} передаёт {@code year} числом. С ключом {@code © {0} 1-Padel. …}
 * {@code MessageFormat} ставил разделитель разрядов по локали: {@code © 2.026} (es),
 * {@code © 2 026} (ru, неразрывный пробел), {@code © 2,026} (en). Теперь в ключе
 * {@code {0,number,#}}, и год выводится как {@code 2026}. Подвал общий для 8 шаблонов,
 * проверяем на {@code email/tournament-confirmation}.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key",
})
class EmailFooterYearTemplateTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @ParameterizedTest
    @ValueSource(strings = {"es", "ru", "en"})
    void footerYear_rendersWithoutGroupingSeparator_inAnyLocale(String languageTag) {
        Context context = contextFor(Locale.forLanguageTag(languageTag));

        String html = templateEngine.process("email/tournament-confirmation", context);

        assertThat(html).contains("© 2026 1-Padel.");
    }

    private Context contextFor(Locale locale) {
        Context context = new Context(locale);
        context.setVariable("playerName", "Juan");
        context.setVariable("tournamentName", "Torneo de prueba");
        context.setVariable("tournamentDate", "25/10/2026");
        context.setVariable("tournamentTime", "15:00");
        context.setVariable("clubName", "Club Padel Central");
        context.setVariable("cancellationDeadline", "24/10/2026 15:00");
        context.setVariable("baseUrl", "https://1-padel.com");
        context.setVariable("year", 2026);
        return context;
    }
}
