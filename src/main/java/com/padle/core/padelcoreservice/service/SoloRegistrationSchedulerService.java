package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

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
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DEADLINE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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
                String tournamentUrl = baseUrl + "/torneo/" + reg.getTournament().getId();
                String clubName = resolveClubName(reg.getTournament().getClubId());

                // LFPT-0668: дедлайн — точное время старта - 24ч (а не календарная дата,
                // как раньше), единая точка правды с moveExpiredAddLaterToReserve.
                String deadline = reg.getTournament().calculateDefaultDeadline().format(DEADLINE_FMT);

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
     * LFPT-0668: каждые 5 минут переводим SOLO_ADD_LATER-заявки, у которых точный дедлайн
     * (старт турнира - 24ч, Tournament.calculateDefaultDeadline()) уже прошёл, в резерв
     * (WAITLIST) вместо тупикового SOLO_EXPIRED — данные не теряются, партнёра можно
     * добавить позже, слот в основном составе освобождается.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void moveExpiredAddLaterToReserve() {
        LocalDateTime now = LocalDateTime.now();
        List<TournamentRegistration> candidates = registrationRepository.findActiveSoloAddLater().stream()
                .filter(reg -> !reg.getTournament().calculateDefaultDeadline().isAfter(now))
                .toList();

        int count = 0;
        for (TournamentRegistration reg : candidates) {
            try {
                Tournament tournament = reg.getTournament();
                int waitlistPosition = registrationRepository.findMaxWaitlistPosition(tournament.getId())
                        .orElse(0) + 1;

                reg.moveToWaitlist(waitlistPosition);
                reg.setPartnerActionToken(generatePartnerActionToken());
                registrationRepository.save(reg);
                count++;

                sendReserveEmail(reg, tournament);

                log.info("Solo registration moved to reserve: tournamentId={}, playerId={}",
                        tournament.getId(), reg.getPlayer().getId());
            } catch (Exception e) {
                log.error("Error moving solo registration {} to reserve: {}", reg.getId(), e.getMessage());
            }
        }

        if (count > 0) {
            log.info("Solo registrations moved to reserve: {}", count);
        }
    }

    /**
     * LFPT-0668: каждые 5 минут — повторное напоминание (с self-service действиями) резервным
     * заявкам без партнёра, если до старта турнира осталось ровно ~3 часа и напоминание ещё
     * не отправлялось.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void sendFinalReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<TournamentRegistration> candidates = registrationRepository
                .findReserveWithoutPartnerPendingFinalReminder().stream()
                .filter(reg -> {
                    LocalDateTime finalReminderAt = reg.getTournament().getFechaHoraInicioCompleta().minusHours(3);
                    return !finalReminderAt.isAfter(now) && !reg.getTournament().getFechaHoraInicioCompleta().isBefore(now);
                })
                .toList();

        int sent = 0;
        for (TournamentRegistration reg : candidates) {
            try {
                if (reg.getPartnerActionToken() == null) {
                    reg.setPartnerActionToken(generatePartnerActionToken());
                }
                sendFinalReminderEmail(reg, reg.getTournament());
                reg.setFinalReminderSent(true);
                registrationRepository.save(reg);
                sent++;
            } catch (Exception e) {
                log.error("Error sending final reminder for registration {}: {}", reg.getId(), e.getMessage());
            }
        }

        if (sent > 0) {
            log.info("Partner final reminders sent: {}", sent);
        }
    }

    private void sendReserveEmail(TournamentRegistration reg, Tournament tournament) {
        String tournamentUrl = baseUrl + "/torneo/" + tournament.getId();
        String clubName = resolveClubName(tournament.getClubId());
        String timeStr = tournament.getHoraInicio() != null ? tournament.getHoraInicio().format(TIME_FMT) : "";

        emailService.sendPartnerReserveEmail(
                reg.getPlayer().getEmail(),
                reg.getPlayer().getNombre(),
                tournament.getNombre(),
                tournament.getFechaInicio().format(DATE_FMT),
                timeStr,
                clubName,
                tournamentUrl,
                reg.getPlayer().getLocale()
        );
    }

    private void sendFinalReminderEmail(TournamentRegistration reg, Tournament tournament) {
        String tournamentUrl = baseUrl + "/torneo/" + tournament.getId();
        String clubName = resolveClubName(tournament.getClubId());
        String timeStr = tournament.getHoraInicio() != null ? tournament.getHoraInicio().format(TIME_FMT) : "";
        String confirmUrl = baseUrl + "/reserva/accion/confirmar?token=" + reg.getPartnerActionToken();
        String declineUrl = baseUrl + "/reserva/accion/cancelar?token=" + reg.getPartnerActionToken();

        emailService.sendPartnerFinalReminderEmail(
                reg.getPlayer().getEmail(),
                reg.getPlayer().getNombre(),
                tournament.getNombre(),
                timeStr,
                clubName,
                tournamentUrl,
                confirmUrl,
                declineUrl,
                reg.getPlayer().getLocale()
        );
    }

    // LFPT-0668: токен self-service действий (подтвердить/отменить) для резервной заявки без
    // партнёра — та же схема, что и у DoubleTournamentRegistrationService.generatePartnerActionToken.
    private String generatePartnerActionToken() {
        return "PAT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    private String resolveClubName(Long clubId) {
        if (clubId == null) return "";
        return clubRepository.findById(clubId)
                .map(c -> c.getNombre())
                .orElse("");
    }
}
