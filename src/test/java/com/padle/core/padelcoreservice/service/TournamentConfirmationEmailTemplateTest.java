package com.padle.core.padelcoreservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * email/tournament-confirmation.html показывает реальный дедлайн отмены турнира
 * (индивидуальный или по умолчанию), а не фиксированные "24 horas"/"24 часа": текст берёт уже
 * отформатированное значение (Tournament.getFormattedCancellationDeadline()) из переменной
 * "cancellationDeadline".
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
class TournamentConfirmationEmailTemplateTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Test
    void plantilla_muestraElDeadlineIndividualEnEspanol_noVeinticuatroHoras() {
        Context context = contextFor(new Locale("es"), "23/10/2026 15:00");

        String html = templateEngine.process("email/tournament-confirmation", context);

        assertThat(html).contains("hasta el 23/10/2026 15:00");
        assertThat(html).doesNotContain("24 horas");
    }

    @Test
    void plantilla_muestraElDeadlinePorDefectoEnRuso_noVeinticuatroHoras() {
        Context context = contextFor(new Locale("ru"), "24/10/2026 15:00");

        String html = templateEngine.process("email/tournament-confirmation", context);

        assertThat(html).contains("до 24/10/2026 15:00");
        assertThat(html).doesNotContain("24 часа");
    }

    @Test
    void plantilla_muestraElDeadlineEnIngles_noVeinticuatroHours() {
        Context context = contextFor(Locale.ENGLISH, "23/10/2026 15:00");

        String html = templateEngine.process("email/tournament-confirmation", context);

        assertThat(html).contains("until 23/10/2026 15:00");
        assertThat(html).doesNotContain("24 hours");
    }

    private Context contextFor(Locale locale, String cancellationDeadline) {
        Context context = new Context(locale);
        context.setVariable("playerName", "Juan");
        context.setVariable("tournamentName", "Torneo de prueba");
        context.setVariable("tournamentDate", "25/10/2026");
        context.setVariable("tournamentTime", "15:00");
        context.setVariable("clubName", "Club Padel Central");
        context.setVariable("cancellationDeadline", cancellationDeadline);
        context.setVariable("baseUrl", "https://1-padel.com");
        context.setVariable("year", 2026);
        return context;
    }
}
