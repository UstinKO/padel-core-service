package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class SoloRegistrationSchedulerService {

    private final TournamentRegistrationRepository registrationRepository;
    private final EmailService emailService;
    private final ClubRepository clubRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Каждый час: отправляем 48h-напоминание игрокам с SOLO_ADD_LATER,
     * если до турнира осталось от 47 до 49 часов (окно ±1 час вокруг 48h).
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void sendPartnerDataReminders() {
        LocalDate from = LocalDate.now().plusDays(1);  // минимум 24h → до начала > 1 дня
        LocalDate to   = LocalDate.now().plusDays(3);  // максимум 72h → начало через < 3 дней

        // Фактически ловим ~48h-окно: турнир послезавтра
        LocalDate reminderDay = LocalDate.now().plusDays(2);
        List<TournamentRegistration> candidates =
                registrationRepository.findSoloAddLaterForReminder(reminderDay, reminderDay.plusDays(1));

        int sent = 0;
        for (TournamentRegistration reg : candidates) {
            try {
                String tournamentUrl = baseUrl + "/tournaments/" + reg.getTournament().getId();
                String clubName = resolveClubName(reg.getTournament().getClubId());

                // Дедлайн — за 24h до начала турнира
                String deadline = reg.getTournament().getFechaInicio().minusDays(1)
                        .format(DATE_FMT);

                emailService.sendPartnerDataReminder(
                        reg.getPlayer().getEmail(),
                        reg.getPlayer().getNombreCompleto(),
                        reg.getTournament().getNombre(),
                        reg.getTournament().getFechaInicio().format(DATE_FMT),
                        clubName,
                        deadline,
                        tournamentUrl,
                        reg.getPlayer().getLocale()
                );

                reg.setSoloReminderSent(true);
                registrationRepository.save(reg);
                sent++;

            } catch (Exception e) {
                log.error("Error sending partner data reminder for registration {}: {}",
                        reg.getId(), e.getMessage());
            }
        }

        if (sent > 0) {
            log.info("Partner data reminders sent: {}", sent);
        }
    }

    /**
     * Каждый час: меняем статус SOLO_ADD_LATER на SOLO_EXPIRED
     * если до начала турнира осталось менее 24 часов.
     */
    @Scheduled(cron = "0 30 * * * *")
    @Transactional
    public void expireIncompleteRegistrations() {
        LocalDate deadline = LocalDate.now().plusDays(1); // турнир начинается раньше чем через сутки

        List<TournamentRegistration> expired =
                registrationRepository.findSoloAddLaterExpired(deadline);

        int count = 0;
        for (TournamentRegistration reg : expired) {
            try {
                reg.setStatus(RegistrationStatus.SOLO_EXPIRED);
                registrationRepository.save(reg);
                count++;
                log.info("Solo registration expired: tournamentId={}, playerId={}",
                        reg.getTournament().getId(), reg.getPlayer().getId());
            } catch (Exception e) {
                log.error("Error expiring solo registration {}: {}", reg.getId(), e.getMessage());
            }
        }

        if (count > 0) {
            log.info("Solo registrations marked as SOLO_EXPIRED: {}", count);
        }
    }

    private String resolveClubName(Long clubId) {
        if (clubId == null) return "";
        return clubRepository.findById(clubId)
                .map(c -> c.getNombre())
                .orElse("");
    }
}
