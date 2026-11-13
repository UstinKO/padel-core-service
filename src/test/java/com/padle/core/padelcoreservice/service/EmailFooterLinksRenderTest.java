package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.metrics.EmailMetricsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class EmailFooterLinksRenderTest {

    private static final String BASE_URL = "https://padel.example";
    private static final String SUPPORT = "help@padel.example";
    private static final String TO = "player@example.com";
    private static final String PLAYER = "Lucía";
    private static final String TOURNAMENT = "Americano";
    private static final String CLUB = "Club Padel";
    private static final String DATE = "25/11/2026";
    private static final String TIME = "15:00";
    private static final Long TOURNAMENT_ID = 42L;
    private static final String TOURNAMENT_URL = BASE_URL + "/torneo/" + TOURNAMENT_ID;
    private static final Locale LOCALE = Locale.forLanguageTag("es");

    private static final Path TEMPLATES = Path.of("src/main/resources/templates/email");
    private static final Pattern ANCHOR = Pattern.compile("<a\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF = Pattern.compile("\\shref=\"([^\"]+)\"");

    @Test
    void everyEmailTemplateRendersWithUsableLinks() throws IOException, MessagingException {
        var sender = new CapturingMailSender();
        var service = emailService(sender);
        Map<String, Runnable> cases = emailCases(service);

        try (var files = Files.list(TEMPLATES)) {
            assertThat(cases.keySet()).containsExactlyInAnyOrderElementsOf(files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".html"))
                    .map(name -> name.substring(0, name.length() - ".html".length()))
                    .toList());
        }

        for (var entry : cases.entrySet()) {
            sender.message = null;
            entry.getValue().run();
            assertThat(sender.message).as("Отправка %s", entry.getKey()).isNotNull();
            sender.message.saveChanges();
            String html = htmlBody(sender.message);
            List<String> links = ANCHOR.matcher(html).results()
                    .map(anchor -> href(anchor.group(), entry.getKey()))
                    .toList();
            long expectedAnchors = ANCHOR.matcher(Files.readString(TEMPLATES.resolve(entry.getKey() + ".html")))
                    .results().count();
            assertThat(links).as("Ссылки в %s", entry.getKey()).hasSize((int) expectedAnchors);
            assertThat(html).doesNotContain("Cancela tu suscripción");

            switch (entry.getKey()) {
                case "bienvenida", "confirmacion", "waitlist-auto-confirm", "no-spots-left" ->
                        assertThat(links).contains(BASE_URL + "/terminos", BASE_URL + "/privacidad", "mailto:" + SUPPORT);
                case "recuperar-password", "tournament-confirmation", "waitlist-notification" ->
                        assertThat(links).contains("mailto:" + SUPPORT);
                default -> {
                }
            }
            if (entry.getKey().equals("participation-request")) {
                assertThat(links).containsExactly(BASE_URL + "/participacion/398af312-ef63-4919-a534-e67c9320de65");
                assertThat(html).contains("compañero invitado", "Alex");
            }
            if (entry.getKey().equals("no-spots-left")) {
                assertThat(links).contains(TOURNAMENT_URL, BASE_URL + "/torneos");
            }
        }
    }

    private static String href(String anchor, String template) {
        var matcher = HREF.matcher(anchor);
        assertThat(matcher.find()).as("href в %s: %s", template, anchor).isTrue();
        String href = matcher.group(1);
        URI uri = URI.create(href);
        assertThat(uri.getScheme()).as("Схема ссылки %s в %s", href, template)
                .isIn("http", "https", "mailto");
        if (!"mailto".equals(uri.getScheme())) {
            assertThat(uri.getHost()).as("Хост ссылки %s в %s", href, template).isNotBlank();
            assertThat(uri.getRawPath()).doesNotContain("//");
        }
        return href;
    }

    private static Map<String, Runnable> emailCases(EmailService service) {
        var startsAt = OffsetDateTime.parse("2026-11-25T15:00:00-03:00");
        var freeUntil = startsAt.minusDays(1);
        var price = BigDecimal.valueOf(10_000);
        var offer = new PairTermsService.Offer("token", 1, TOURNAMENT, startsAt, freeUntil,
                price, price.add(price), price, "ARS", "C7", BASE_URL + "/terminos");
        var cancellation = new PairTermsService.Cancellation("token", false, true, true, true, freeUntil,
                "ARS", price, BigDecimal.ZERO, price, BigDecimal.ZERO, "", "");
        var notice = new PairReplacementNoticeService.Notice(1L, TOURNAMENT, PLAYER, "Alex");

        Map<String, Runnable> cases = new LinkedHashMap<>();
        cases.put("admin-solo-registration", () -> service.sendAdminSoloRegistrationNotification(
                TO, PLAYER, TO, "+5491111111111", "@player", TOURNAMENT, DATE, CLUB, "SEARCH",
                BASE_URL + "/admin/tournaments/" + TOURNAMENT_ID));
        cases.put("bienvenida", () -> service.sendWelcomeEmail(TO, PLAYER, LOCALE));
        cases.put("cancha-abierta-reminder", () -> service.sendCanchaAbiertaReminderEmail(
                TO, PLAYER, CLUB, "Av. Central", TIME, TOURNAMENT_ID, LOCALE));
        cases.put("confirmacion", () -> service.sendConfirmationEmail(TO, PLAYER, "code", LOCALE));
        cases.put("guest-partner-notice", () -> service.sendGuestPartnerNotice(
                TO, PLAYER, TOURNAMENT, GuestPartnerNotice.Kind.CONFIRMED, LOCALE));
        cases.put("individual-tournament-reminder", () -> service.sendIndividualTournamentReminderEmail(
                TO, PLAYER, CLUB, "Av. Central", TIME, TOURNAMENT_ID, LOCALE));
        cases.put("looking-for-partner-notification", () -> service.sendLookingForPartnerNotification(
                TO, PLAYER, TOURNAMENT, DATE, TOURNAMENT_URL, LOCALE));
        cases.put("new-partner-invitation", () -> service.sendNewPartnerInvitationEmail(
                TO, PLAYER, "Alex", TOURNAMENT, DATE, TIME, CLUB, BASE_URL + "/complete", 24, LOCALE));
        cases.put("no-spots-left", () -> service.sendNoSpotsLeftEmail(TO, PLAYER, TOURNAMENT, TOURNAMENT_ID, LOCALE));
        cases.put("participation-request", () -> service.sendParticipationRequestEmail(
                TO, "Alex", TOURNAMENT, UUID.fromString("398af312-ef63-4919-a534-e67c9320de65"), true, LOCALE));
        cases.put("pair-proposal", () -> service.sendPairProposalEmail(
                TO, PLAYER, "Alex", TOURNAMENT, DATE, BASE_URL + "/accept", LOCALE));
        cases.put("pair-terms", () -> service.sendPairTermsEmail(TO, PLAYER, CLUB, offer, true, LOCALE));
        cases.put("pair-tournament-reminder", () -> service.sendPairTournamentReminderEmail(
                TO, PLAYER, CLUB, "Av. Central", TIME, TOURNAMENT_ID, true, LOCALE));
        cases.put("partner-data-reminder", () -> service.sendPartnerDataReminder(
                TO, PLAYER, TOURNAMENT, DATE, CLUB, DATE, TOURNAMENT_URL, LOCALE));
        cases.put("partner-deadline-reserve", () -> service.sendPartnerReserveEmail(
                TO, PLAYER, TOURNAMENT, DATE, TIME, CLUB, TOURNAMENT_URL, LOCALE));
        cases.put("partner-final-reminder", () -> service.sendPartnerFinalReminderEmail(
                TO, PLAYER, TOURNAMENT, TIME, CLUB, TOURNAMENT_URL, BASE_URL + "/confirm", BASE_URL + "/decline", LOCALE));
        cases.put("partner-replaced", () -> service.sendPartnerReplacedEmail(TO, notice, LOCALE));
        cases.put("recuperar-password", () -> service.sendPasswordResetEmail(TO, PLAYER, BASE_URL + "/reset", LOCALE));
        cases.put("registration-cancelled", () -> service.sendPairCancellationEmail(
                TO, PLAYER, TOURNAMENT, DATE, TIME, CLUB, cancellation, LOCALE));
        cases.put("registration-moved-to-reserve", () -> service.sendMovedToReserveEmail(
                TO, PLAYER, TOURNAMENT, DATE, TIME, CLUB, LOCALE));
        cases.put("tournament-confirmation", () -> service.sendRegistrationConfirmationEmail(
                TO, PLAYER, TOURNAMENT, DATE, TIME, CLUB, DATE, LOCALE));
        cases.put("waitlist-auto-confirm", () -> service.sendWaitlistAutoConfirmEmail(
                TO, PLAYER, TOURNAMENT, DATE, TIME, CLUB, TOURNAMENT_URL, LOCALE));
        cases.put("waitlist-notification", () -> service.sendWaitlistNotificationEmail(
                TO, PLAYER, TOURNAMENT, DATE, TIME, CLUB, 2, LOCALE));
        return cases;
    }

    private static EmailService emailService(CapturingMailSender sender) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");

        var messages = new ResourceBundleMessageSource();
        messages.setBasenames("i18n/messages", "i18n/guest-claim");
        messages.setDefaultEncoding("UTF-8");

        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(messages);

        var service = new EmailService(sender, engine, new EmailMetricsService(new SimpleMeterRegistry()), messages);
        service.setBaseUrl(BASE_URL + "/");
        ReflectionTestUtils.setField(service, "supportAddress", SUPPORT);
        ReflectionTestUtils.setField(service, "dailyEmailLimit", 300);
        ReflectionTestUtils.setField(service, "fromEmail", "no-reply@padel.example");
        return service;
    }

    private static String htmlBody(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        }
        var multipart = (MimeMultipart) part.getContent();
        return htmlBody(multipart.getBodyPart(0));
    }

    private static final class CapturingMailSender extends JavaMailSenderImpl {
        private MimeMessage message;

        @Override
        public void send(MimeMessage mimeMessage) {
            message = mimeMessage;
        }
    }
}
