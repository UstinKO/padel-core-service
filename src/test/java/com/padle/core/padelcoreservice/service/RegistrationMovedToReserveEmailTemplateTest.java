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
 * email/registration-moved-to-reserve.html — используется, когда администратор переводит
 * уже подтверждённого игрока в резерв (LFPT-0752). Текст явно говорит, что регистрация не
 * отменена — в отличие от email/waitlist-notification.html (первичная постановка в очередь
 * при самой регистрации).
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
class RegistrationMovedToReserveEmailTemplateTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Test
    void enEspanol_explicaQueNoFueCancelada() {
        Context context = contextFor(new Locale("es"));

        String html = templateEngine.process("email/registration-moved-to-reserve", context);

        assertThat(html).contains("no fue cancelada");
        assertThat(html).contains("lista de reserva");
        assertThat(html).doesNotContain("agregado a la");
    }

    @Test
    void enRuso_explicaQueNoFueCancelada() {
        Context context = contextFor(new Locale("ru"));

        String html = templateEngine.process("email/registration-moved-to-reserve", context);

        assertThat(html).contains("не отменена");
        assertThat(html).contains("резерва");
    }

    @Test
    void enIngles_explicaQueNoFueCancelada() {
        Context context = contextFor(Locale.ENGLISH);

        String html = templateEngine.process("email/registration-moved-to-reserve", context);

        assertThat(html).contains("was not cancelled");
        assertThat(html).contains("reserve list");
    }

    private Context contextFor(Locale locale) {
        Context context = new Context(locale);
        context.setVariable("playerName", "Juan");
        context.setVariable("tournamentName", "Torneo de prueba");
        context.setVariable("tournamentDate", "25/10/2026");
        context.setVariable("tournamentTime", "15:00");
        context.setVariable("clubName", "Club Padel Central");
        context.setVariable("year", 2026);
        return context;
    }
}
