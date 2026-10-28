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
 * email/registration-cancelled.html — используется при отмене регистрации на турнир,
 * независимо от инициатора (игрок/админ/система, LFPT-0752). Причина отмены показывается
 * только если она есть; текст локализован по ES/RU/EN.
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
class RegistrationCancelledEmailTemplateTest {

    @Autowired
    private SpringTemplateEngine templateEngine;

    @Test
    void conMotivo_muestraElMotivoEnEspanol() {
        Context context = contextFor(new Locale("es"), "No llega a tiempo");

        String html = templateEngine.process("email/registration-cancelled", context);

        assertThat(html).contains("Tu participación en el siguiente torneo fue cancelada");
        assertThat(html).contains("No llega a tiempo");
        assertThat(html).contains("Motivo");
    }

    @Test
    void sinMotivo_noMuestraElBloqueDeMotivo() {
        Context context = contextFor(new Locale("es"), null);

        String html = templateEngine.process("email/registration-cancelled", context);

        assertThat(html).contains("Tu participación en el siguiente torneo fue cancelada");
        assertThat(html).doesNotContain("Motivo");
    }

    @Test
    void conMotivoEnRuso_muestraElTextoRuso() {
        Context context = contextFor(new Locale("ru"), "Не сможет прийти");

        String html = templateEngine.process("email/registration-cancelled", context);

        assertThat(html).contains("Ваше участие в следующем турнире было отменено");
        assertThat(html).contains("Не сможет прийти");
        assertThat(html).contains("Причина");
    }

    @Test
    void conMotivoEnIngles_muestraElTextoEnIngles() {
        Context context = contextFor(Locale.ENGLISH, "Cannot attend");

        String html = templateEngine.process("email/registration-cancelled", context);

        assertThat(html).contains("Your participation in the following tournament was cancelled");
        assertThat(html).contains("Cannot attend");
        assertThat(html).contains("Reason");
    }

    private Context contextFor(Locale locale, String cancellationReason) {
        Context context = new Context(locale);
        context.setVariable("playerName", "Juan");
        context.setVariable("tournamentName", "Torneo de prueba");
        context.setVariable("tournamentDate", "25/10/2026");
        context.setVariable("tournamentTime", "15:00");
        context.setVariable("clubName", "Club Padel Central");
        context.setVariable("cancellationReason", cancellationReason);
        context.setVariable("year", 2026);
        return context;
    }
}
