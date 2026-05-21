package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.annotation.Counted;
import com.padle.core.padelcoreservice.annotation.MetricTag;
import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.annotation.TrackErrors;
import com.padle.core.padelcoreservice.metrics.EmailMetricsService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final EmailMetricsService emailMetricsService;  // ← ДОБАВЛЕНО

    @Value("${app.email.from:noreply@padelcore.com}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.email.daily-limit:300}")
    private int dailyEmailLimit;  // ← ДОБАВЛЕНО

    /**
     * Отправка письма для подтверждения email
     */
    @Timed(
            name = "email.send.time",
            description = "Time taken to send email",
            tags = {"service=email", "type=confirmation"}
    )
    @Counted(
            name = "email.send.attempts",
            description = "Total email send attempts",
            tags = {"service=email", "type=confirmation"}
    )
    @TrackErrors(
            name = "email.send.errors",
            tags = {"service=email", "type=confirmation"}
    )
    public void sendConfirmationEmail(
            @MetricTag("recipient") String to,
            @MetricTag("playerName") String nombre,
            String codigo) {

        // Регистрируем попытку
        emailMetricsService.recordEmailAttempt("CONFIRMATION");

        // Проверка лимита
        if (emailMetricsService.isDailyLimitReached(dailyEmailLimit)) {
            log.warn("Daily email limit of {} reached, rejecting confirmation email to: {}", dailyEmailLimit, to);
            emailMetricsService.recordEmailRejected("CONFIRMATION", "DAILY_LIMIT");
            return;
        }

        try {
            log.info("📧 Отправка письма подтверждения на: {}", to);

            Context context = new Context();
            context.setVariable("nombre", nombre);
            context.setVariable("confirmUrl",
                    String.format("%s/players/confirmar-email?codigo=%s", baseUrl, codigo));
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/confirmacion", context);

            sendHtmlEmail(to, "Confirma tu email en Padel Core", htmlContent);

            emailMetricsService.recordEmailSent("CONFIRMATION");  // ← ДОБАВЛЕНО
            log.info("✅ Письмо подтверждения успешно отправлено на: {}", to);

        } catch (Exception e) {
            emailMetricsService.recordEmailError("CONFIRMATION", e.getClass().getSimpleName());  // ← ДОБАВЛЕНО
            log.error("❌ Ошибка отправки письма на {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Error al enviar email de confirmación", e);
        }
    }

    /**
     * Отправка приглашения партнеру (для уже зарегистрированного пользователя)
     */
    @Timed(
            name = "email.send.time",
            tags = {"service=email", "type=partner_invitation"}
    )
    @Counted(
            name = "email.send.attempts",
            tags = {"service=email", "type=partner_invitation"}
    )
    @TrackErrors(
            name = "email.send.errors",
            tags = {"service=email", "type=partner_invitation"}
    )
    public void sendPartnerInvitationEmail(
            @MetricTag("recipient") String to,
            @MetricTag("partnerName") String partnerName,
            @MetricTag("mainPlayerName") String mainPlayerName,
            String tournamentName,
            String tournamentDate,
            String tournamentTime,
            String clubName) {

        emailMetricsService.recordEmailAttempt("PARTNER_INVITATION");

        if (emailMetricsService.isDailyLimitReached(dailyEmailLimit)) {
            log.warn("Daily email limit reached, rejecting partner invitation to: {}", to);
            emailMetricsService.recordEmailRejected("PARTNER_INVITATION", "DAILY_LIMIT");
            return;
        }

        try {
            log.info("📧 Enviando invitación a compañero (registrado) a: {}", to);

            Context context = new Context();
            context.setVariable("partnerName", partnerName);
            context.setVariable("mainPlayerName", mainPlayerName);
            context.setVariable("tournamentName", tournamentName);
            context.setVariable("tournamentDate", tournamentDate);
            context.setVariable("tournamentTime", tournamentTime);
            context.setVariable("clubName", clubName);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/partner-invitation", context);

            sendHtmlEmail(to, "🎾 Te han inscrito en un torneo de dobles - E-Padel", htmlContent);

            emailMetricsService.recordEmailSent("PARTNER_INVITATION");
            log.info("✅ Invitación a compañero enviada a: {}", to);

        } catch (Exception e) {
            emailMetricsService.recordEmailError("PARTNER_INVITATION", e.getClass().getSimpleName());
            log.error("❌ Error enviando invitación a compañero a {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Отправка приглашения новому партнеру (незарегистрированному)
     */
    @Timed(
            name = "email.send.time",
            tags = {"service=email", "type=new_partner_invitation"}
    )
    @Counted(
            name = "email.send.attempts",
            tags = {"service=email", "type=new_partner_invitation"}
    )
    @TrackErrors(
            name = "email.send.errors",
            tags = {"service=email", "type=new_partner_invitation"}
    )
    public void sendNewPartnerInvitationEmail(
            @MetricTag("recipient") String to,
            @MetricTag("partnerName") String partnerName,
            @MetricTag("mainPlayerName") String mainPlayerName,
            String tournamentName,
            String tournamentDate,
            String tournamentTime,
            String clubName,
            String completionUrl,
            int expiryHours) {

        emailMetricsService.recordEmailAttempt("NEW_PARTNER_INVITATION");

        if (emailMetricsService.isDailyLimitReached(dailyEmailLimit)) {
            log.warn("Daily email limit reached, rejecting new partner invitation to: {}", to);
            emailMetricsService.recordEmailRejected("NEW_PARTNER_INVITATION", "DAILY_LIMIT");
            return;
        }

        try {
            log.info("📧 Enviando invitación a nuevo compañero a: {}", to);

            Context context = new Context();
            context.setVariable("partnerName", partnerName);
            context.setVariable("mainPlayerName", mainPlayerName);
            context.setVariable("tournamentName", tournamentName);
            context.setVariable("tournamentDate", tournamentDate);
            context.setVariable("tournamentTime", tournamentTime);
            context.setVariable("clubName", clubName);
            context.setVariable("completionUrl", completionUrl);
            context.setVariable("expiryHours", expiryHours);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/new-partner-invitation", context);

            sendHtmlEmail(to, "🎾 Te han invitado a jugar un torneo de dobles - E-Padel", htmlContent);

            emailMetricsService.recordEmailSent("NEW_PARTNER_INVITATION");
            log.info("✅ Invitación a nuevo compañero enviada a: {}", to);

        } catch (Exception e) {
            emailMetricsService.recordEmailError("NEW_PARTNER_INVITATION", e.getClass().getSimpleName());
            log.error("❌ Error enviando invitación a nuevo compañero a {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Отправка подтверждения регистрации пары
     */
    @Timed(
            name = "email.send.time",
            tags = {"service=email", "type=pair_confirmation"}
    )
    @Counted(
            name = "email.send.attempts",
            tags = {"service=email", "type=pair_confirmation"}
    )
    @TrackErrors(
            name = "email.send.errors",
            tags = {"service=email", "type=pair_confirmation"}
    )
    public void sendPairConfirmationEmail(
            @MetricTag("recipient") String to,
            @MetricTag("playerName") String playerName,
            @MetricTag("partnerName") String partnerName,
            String tournamentName,
            String tournamentDate,
            String tournamentTime,
            String clubName) {

        emailMetricsService.recordEmailAttempt("PAIR_CONFIRMATION");

        if (emailMetricsService.isDailyLimitReached(dailyEmailLimit)) {
            log.warn("Daily email limit reached, rejecting pair confirmation to: {}", to);
            emailMetricsService.recordEmailRejected("PAIR_CONFIRMATION", "DAILY_LIMIT");
            return;
        }

        try {
            log.info("📧 Enviando confirmación de pareja a: {}", to);

            Context context = new Context();
            context.setVariable("playerName", playerName);
            context.setVariable("partnerName", partnerName);
            context.setVariable("tournamentName", tournamentName);
            context.setVariable("tournamentDate", tournamentDate);
            context.setVariable("tournamentTime", tournamentTime);
            context.setVariable("clubName", clubName);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/pair-confirmation", context);

            sendHtmlEmail(to, "🎾 ¡Pareja confirmada para el torneo! - E-Padel", htmlContent);

            emailMetricsService.recordEmailSent("PAIR_CONFIRMATION");
            log.info("✅ Confirmación de pareja enviada a: {}", to);

        } catch (Exception e) {
            emailMetricsService.recordEmailError("PAIR_CONFIRMATION", e.getClass().getSimpleName());
            log.error("❌ Error enviando confirmación de pareja a {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Отправка приветственного письма
     */
    @Timed(name = "email.send.time", tags = {"service=email", "type=welcome"})
    @Counted(name = "email.send.attempts", tags = {"service=email", "type=welcome"})
    @TrackErrors(name = "email.send.errors", tags = {"service=email", "type=welcome"})
    public void sendWelcomeEmail(@MetricTag("recipient") String to, @MetricTag("playerName") String nombre) {
        emailMetricsService.recordEmailAttempt("WELCOME");

        if (emailMetricsService.isDailyLimitReached(dailyEmailLimit)) {
            log.warn("Daily email limit reached, rejecting welcome email to: {}", to);
            emailMetricsService.recordEmailRejected("WELCOME", "DAILY_LIMIT");
            return;
        }

        try {
            log.info("📧 Отправка приветственного письма на: {}", to);

            Context context = new Context();
            context.setVariable("nombre", nombre);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("dashboardUrl", baseUrl + "/players/dashboard");
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/bienvenida", context);

            sendHtmlEmail(to, "¡Bienvenido a Padel Core!", htmlContent);

            emailMetricsService.recordEmailSent("WELCOME");
            log.info("✅ Приветственное письмо успешно отправлено на: {}", to);

        } catch (Exception e) {
            emailMetricsService.recordEmailError("WELCOME", e.getClass().getSimpleName());
            log.error("❌ Ошибка отправки приветствия на {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Отправка письма с приглашением из листа ожидания
     */
    @Timed(name = "email.send.time", tags = {"service=email", "type=vacancy_invitation"})
    @Counted(name = "email.send.attempts", tags = {"service=email", "type=vacancy_invitation"})
    @TrackErrors(name = "email.send.errors", tags = {"service=email", "type=vacancy_invitation"})
    public void sendVacancyInvitationEmail(
            @MetricTag("recipient") String to,
            @MetricTag("playerName") String playerName,
            String tournamentName,
            String tournamentDate,
            String tournamentTime,
            String clubName,
            String confirmationUrl,
            java.time.LocalDateTime deadline) {

        emailMetricsService.recordEmailAttempt("VACANCY_INVITATION");

        if (emailMetricsService.isDailyLimitReached(dailyEmailLimit)) {
            log.warn("Daily email limit reached, rejecting vacancy invitation to: {}", to);
            emailMetricsService.recordEmailRejected("VACANCY_INVITATION", "DAILY_LIMIT");
            return;
        }

        try {
            log.info("📧 Отправка приглашения из листа ожидания на: {}", to);

            String deadlineStr = deadline != null
                    ? deadline.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    : "";

            Context context = new Context();
            context.setVariable("playerName", playerName);
            context.setVariable("tournamentName", tournamentName);
            context.setVariable("tournamentDate", tournamentDate);
            context.setVariable("tournamentTime", tournamentTime);
            context.setVariable("clubName", clubName);
            context.setVariable("confirmationUrl", confirmationUrl);
            context.setVariable("deadline", deadlineStr);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/vacancy-invitation", context);

            sendHtmlEmail(to, "🎾 ¡Se ha liberado un lugar en el torneo! Confirma tu participación", htmlContent);

            emailMetricsService.recordEmailSent("VACANCY_INVITATION");
            log.info("✅ Приглашение из листа ожидания отправлено на: {}", to);

        } catch (Exception e) {
            emailMetricsService.recordEmailError("VACANCY_INVITATION", e.getClass().getSimpleName());
            log.error("❌ Ошибка отправки приглашения на {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Отправка письма о том, что мест больше нет
     */
    @Timed(name = "email.send.time", tags = {"service=email", "type=no_spots_left"})
    @Counted(name = "email.send.attempts", tags = {"service=email", "type=no_spots_left"})
    @TrackErrors(name = "email.send.errors", tags = {"service=email", "type=no_spots_left"})
    public void sendNoSpotsLeftEmail(
            @MetricTag("recipient") String to,
            @MetricTag("playerName") String playerName,
            String tournamentName) {

        emailMetricsService.recordEmailAttempt("NO_SPOTS_LEFT");

        if (emailMetricsService.isDailyLimitReached(dailyEmailLimit)) {
            log.warn("Daily email limit reached, rejecting no spots left email to: {}", to);
            emailMetricsService.recordEmailRejected("NO_SPOTS_LEFT", "DAILY_LIMIT");
            return;
        }

        try {
            log.info("📧 Отправка письма о занятых местах на: {}", to);

            Context context = new Context();
            context.setVariable("playerName", playerName);
            context.setVariable("tournamentName", tournamentName);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/no-spots-left", context);

            sendHtmlEmail(to, "😢 No hay más lugares disponibles", htmlContent);

            emailMetricsService.recordEmailSent("NO_SPOTS_LEFT");
            log.info("✅ Письмо о занятых местах отправлено на: {}", to);

        } catch (Exception e) {
            emailMetricsService.recordEmailError("NO_SPOTS_LEFT", e.getClass().getSimpleName());
            log.error("❌ Ошибка отправки письма на {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Отправка подтверждения регистрации
     */
    @Timed(name = "email.send.time", tags = {"service=email", "type=registration_confirmation"})
    @Counted(name = "email.send.attempts", tags = {"service=email", "type=registration_confirmation"})
    @TrackErrors(name = "email.send.errors", tags = {"service=email", "type=registration_confirmation"})
    public void sendRegistrationConfirmationEmail(
            @MetricTag("recipient") String to,
            @MetricTag("playerName") String playerName,
            String tournamentName,
            String tournamentDate,
            String tournamentTime,
            String clubName) {

        emailMetricsService.recordEmailAttempt("REGISTRATION_CONFIRMATION");

        if (emailMetricsService.isDailyLimitReached(dailyEmailLimit)) {
            log.warn("Daily email limit reached, rejecting registration confirmation to: {}", to);
            emailMetricsService.recordEmailRejected("REGISTRATION_CONFIRMATION", "DAILY_LIMIT");
            return;
        }

        try {
            log.info("📧 Отправка подтверждения регистрации на: {}", to);

            Context context = new Context();
            context.setVariable("playerName", playerName);
            context.setVariable("tournamentName", tournamentName);
            context.setVariable("tournamentDate", tournamentDate);
            context.setVariable("tournamentTime", tournamentTime);
            context.setVariable("clubName", clubName);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/tournament-confirmation", context);

            sendHtmlEmail(to, "✅ Tu participación ha sido confirmada", htmlContent);

            emailMetricsService.recordEmailSent("REGISTRATION_CONFIRMATION");
            log.info("✅ Подтверждение регистрации отправлено на: {}", to);

        } catch (Exception e) {
            emailMetricsService.recordEmailError("REGISTRATION_CONFIRMATION", e.getClass().getSimpleName());
            log.error("❌ Ошибка отправки подтверждения на {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Отправка письма для сброса пароля
     */
    @Timed(name = "email.send.time", tags = {"service=email", "type=password_reset"})
    @Counted(name = "email.send.attempts", tags = {"service=email", "type=password_reset"})
    @TrackErrors(name = "email.send.errors", tags = {"service=email", "type=password_reset"})
    public void sendPasswordResetEmail(
            @MetricTag("recipient") String to,
            @MetricTag("playerName") String nombre,
            String resetUrl) {

        emailMetricsService.recordEmailAttempt("PASSWORD_RESET");

        if (emailMetricsService.isDailyLimitReached(dailyEmailLimit)) {
            log.warn("Daily email limit reached, rejecting password reset email to: {}", to);
            emailMetricsService.recordEmailRejected("PASSWORD_RESET", "DAILY_LIMIT");
            return;
        }

        try {
            log.info("📧 Enviando email de recuperación de contraseña a: {}", to);

            Context context = new Context();
            context.setVariable("nombre", nombre);
            context.setVariable("resetUrl", resetUrl);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/recuperar-password", context);

            sendHtmlEmail(to, "🔐 Recuperación de contraseña - E-Padel", htmlContent);

            emailMetricsService.recordEmailSent("PASSWORD_RESET");
            log.info("✅ Email de recuperación enviado a: {}", to);

        } catch (Exception e) {
            emailMetricsService.recordEmailError("PASSWORD_RESET", e.getClass().getSimpleName());
            log.error("❌ Error enviando email de recuperación a {}: {}", to, e.getMessage());
        }
    }

    /**
     * Отправка уведомления о добавлении в лист ожидания
     */
    @Timed(name = "email.send.time", tags = {"service=email", "type=waitlist_notification"})
    @Counted(name = "email.send.attempts", tags = {"service=email", "type=waitlist_notification"})
    @TrackErrors(name = "email.send.errors", tags = {"service=email", "type=waitlist_notification"})
    public void sendWaitlistNotificationEmail(
            @MetricTag("recipient") String to,
            @MetricTag("playerName") String playerName,
            String tournamentName,
            String tournamentDate,
            String tournamentTime,
            String clubName,
            int waitlistPosition) {

        emailMetricsService.recordEmailAttempt("WAITLIST_NOTIFICATION");

        if (emailMetricsService.isDailyLimitReached(dailyEmailLimit)) {
            log.warn("Daily email limit reached, rejecting waitlist notification to: {}", to);
            emailMetricsService.recordEmailRejected("WAITLIST_NOTIFICATION", "DAILY_LIMIT");
            return;
        }

        try {
            log.info("📧 Отправка уведомления о добавлении в лист ожидания на: {}", to);

            Context context = new Context();
            context.setVariable("playerName", playerName);
            context.setVariable("tournamentName", tournamentName);
            context.setVariable("tournamentDate", tournamentDate);
            context.setVariable("tournamentTime", tournamentTime);
            context.setVariable("clubName", clubName);
            context.setVariable("waitlistPosition", waitlistPosition);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/waitlist-notification", context);

            sendHtmlEmail(to, "📋 Has sido agregado a la lista de espera", htmlContent);

            emailMetricsService.recordEmailSent("WAITLIST_NOTIFICATION");
            log.info("✅ Уведомление о листе ожидания отправлено на: {}", to);

        } catch (Exception e) {
            emailMetricsService.recordEmailError("WAITLIST_NOTIFICATION", e.getClass().getSimpleName());
            log.error("❌ Ошибка отправки уведомления на {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Отправка подтверждения регистрации на турнир
     */
    @Timed(name = "email.send.time", tags = {"service=email", "type=tournament_confirmation"})
    @Counted(name = "email.send.attempts", tags = {"service=email", "type=tournament_confirmation"})
    @TrackErrors(name = "email.send.errors", tags = {"service=email", "type=tournament_confirmation"})
    public void sendTournamentConfirmationEmail(
            @MetricTag("recipient") String to,
            @MetricTag("playerName") String playerName,
            String tournamentName,
            String tournamentDate,
            String tournamentTime,
            String clubName) {

        emailMetricsService.recordEmailAttempt("TOURNAMENT_CONFIRMATION");

        if (emailMetricsService.isDailyLimitReached(dailyEmailLimit)) {
            log.warn("Daily email limit reached, rejecting tournament confirmation to: {}", to);
            emailMetricsService.recordEmailRejected("TOURNAMENT_CONFIRMATION", "DAILY_LIMIT");
            return;
        }

        try {
            log.info("📧 Enviando confirmación de inscripción a torneo a: {}", to);

            Context context = new Context();
            context.setVariable("playerName", playerName);
            context.setVariable("tournamentName", tournamentName);
            context.setVariable("tournamentDate", tournamentDate);
            context.setVariable("tournamentTime", tournamentTime);
            context.setVariable("clubName", clubName);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/tournament-confirmation", context);

            sendHtmlEmail(to, "🎾 ¡Inscripción confirmada! - E-Padel", htmlContent);

            emailMetricsService.recordEmailSent("TOURNAMENT_CONFIRMATION");
            log.info("✅ Confirmación de torneo enviada a: {}", to);

        } catch (Exception e) {
            emailMetricsService.recordEmailError("TOURNAMENT_CONFIRMATION", e.getClass().getSimpleName());
            log.error("❌ Error enviando confirmación de torneo a {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Универсальный метод для отправки HTML писем (БЕЗ ИЗМЕНЕНИЙ)
     */
    private void sendHtmlEmail(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(message);
        log.debug("HTML email sent to: {}", to);
    }
}