package com.padle.core.padelcoreservice.service;

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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.email.from:noreply@padelcore.com}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Отправка письма для подтверждения email
     */
    public void sendConfirmationEmail(String to, String nombre, String codigo) {
        try {
            log.info("📧 Отправка письма подтверждения на: {}", to);

            Context context = new Context();
            context.setVariable("nombre", nombre);
            context.setVariable("confirmUrl",
                    String.format("%s/players/confirmar-email?codigo=%s", baseUrl, codigo));
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/confirmacion", context);

            sendHtmlEmail(to, "Confirma tu email en Padel Core", htmlContent);
            log.info("✅ Письмо подтверждения успешно отправлено на: {}", to);

        } catch (Exception e) {
            log.error("❌ Ошибка отправки письма на {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Error al enviar email de confirmación", e);
        }
    }

    /**
     * Отправка приветственного письма
     */
    public void sendWelcomeEmail(String to, String nombre) {
        try {
            log.info("📧 Отправка приветственного письма на: {}", to);

            Context context = new Context();
            context.setVariable("nombre", nombre);
            context.setVariable("baseUrl", baseUrl);
            context.setVariable("dashboardUrl", baseUrl + "/players/dashboard");
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/bienvenida", context);

            sendHtmlEmail(to, "¡Bienvenido a Padel Core!", htmlContent);
            log.info("✅ Приветственное письмо успешно отправлено на: {}", to);

        } catch (Exception e) {
            log.error("❌ Ошибка отправки приветствия на {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Отправка письма с приглашением из листа ожидания
     */
    public void sendVacancyInvitationEmail(String to, String playerName, String tournamentName,
                                           String tournamentDate, String tournamentTime,
                                           String clubName, String confirmationUrl) {
        try {
            log.info("📧 Отправка приглашения из листа ожидания на: {}", to);

            Context context = new Context();
            context.setVariable("playerName", playerName);
            context.setVariable("tournamentName", tournamentName);
            context.setVariable("tournamentDate", tournamentDate);
            context.setVariable("tournamentTime", tournamentTime);
            context.setVariable("clubName", clubName);
            context.setVariable("confirmationUrl", confirmationUrl);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/vacancy-invitation", context);

            sendHtmlEmail(to, "🎾 ¡Se ha liberado un lugar en el torneo! Confirma tu participación", htmlContent);
            log.info("✅ Приглашение из листа ожидания отправлено на: {}", to);

        } catch (Exception e) {
            log.error("❌ Ошибка отправки приглашения на {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Отправка письма о том, что мест больше нет
     */
    public void sendNoSpotsLeftEmail(String to, String playerName, String tournamentName) {
        try {
            log.info("📧 Отправка письма о занятых местах на: {}", to);

            Context context = new Context();
            context.setVariable("playerName", playerName);
            context.setVariable("tournamentName", tournamentName);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/no-spots-left", context);

            sendHtmlEmail(to, "😢 No hay más lugares disponibles", htmlContent);
            log.info("✅ Письмо о занятых местах отправлено на: {}", to);

        } catch (Exception e) {
            log.error("❌ Ошибка отправки письма на {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Отправка подтверждения регистрации
     */
    public void sendRegistrationConfirmationEmail(String to, String playerName, String tournamentName,
                                                  String tournamentDate, String tournamentTime,
                                                  String clubName) {
        try {
            log.info("📧 Отправка подтверждения регистрации на: {}", to);

            Context context = new Context();
            context.setVariable("playerName", playerName);
            context.setVariable("tournamentName", tournamentName);
            context.setVariable("tournamentDate", tournamentDate);
            context.setVariable("tournamentTime", tournamentTime);
            context.setVariable("clubName", clubName);
            context.setVariable("year", java.time.Year.now().getValue());

            String htmlContent = templateEngine.process("email/confirmation", context);

            sendHtmlEmail(to, "✅ Tu participación ha sido confirmada", htmlContent);
            log.info("✅ Подтверждение регистрации отправлено на: {}", to);

        } catch (Exception e) {
            log.error("❌ Ошибка отправки подтверждения на {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * Универсальный метод для отправки HTML писем
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.debug("HTML email sent to: {}", to);
        } catch (MessagingException e) {
            log.error("Error sending HTML email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Error sending email", e);
        }
    }
}