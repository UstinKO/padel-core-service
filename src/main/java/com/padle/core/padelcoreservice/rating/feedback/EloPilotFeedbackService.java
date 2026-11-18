package com.padle.core.padelcoreservice.rating.feedback;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.rating.feedback.EloPilotFeedbackRepository.MailStatus;
import com.padle.core.padelcoreservice.rating.feedback.EloPilotFeedbackRepository.Source;
import com.padle.core.padelcoreservice.rating.feedback.EloPilotFeedbackRepository.Topic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
public class EloPilotFeedbackService {
    private final EloPilotFeedbackRepository repository;
    private final JavaMailSender mailSender;
    private final String recipient;
    private final String sender;
    private final String inboxUrl;

    public EloPilotFeedbackService(EloPilotFeedbackRepository repository, JavaMailSender mailSender,
                                   @Value("${app.rating.pilot.feedback-recipient:${app.admin-email}}") String recipient,
                                   @Value("${app.email.from}") String sender,
                                   @Value("${app.base-url}") String baseUrl) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.recipient = recipient;
        this.sender = sender;
        this.inboxUrl = baseUrl.replaceFirst("/+$", "") + "/admin/rating-feedback";
    }

    public void submit(PlayerPadel player, UUID requestKey, Source source, Topic topic, String message) {
        String author = (player.getNombre() + " " + player.getApellido()).strip();
        // Вставка завершается до SMTP. Повтор того же запроса не создаёт отзыв или второе письмо.
        repository.insert(player.getId(), author, requestKey, source, topic, message)
                .ifPresent(this::notifyAdmin);
    }

    private void notifyAdmin(long id) {
        var mail = new SimpleMailMessage();
        mail.setFrom(sender);
        mail.setTo(recipient);
        mail.setSubject("1-Padel — новый отзыв о рейтинге №" + id);
        mail.setText("Новый отзыв сохранён в админке 1-Padel.\n\n" + inboxUrl + "#" + id);
        MailStatus status = MailStatus.SENT;
        try {
            mailSender.send(mail);
        } catch (MailException | IllegalArgumentException exception) {
            status = MailStatus.FAILED;
            log.warn("Не отправлено уведомление об отзыве Elo №{}: {}", id, exception.getClass().getSimpleName());
        }
        repository.markMail(id, status);
    }
}
