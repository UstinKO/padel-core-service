package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SoloRegistrationSchedulerService {

    private final TournamentRegistrationRepository registrationRepository;
    private final EmailService emailService;
    private final ClubRepository clubRepository;
    private final TournamentRepository tournaments;
    private final EntityManager entities;

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
        // Фактически ловим ~48h-окно: турнир послезавтра
        LocalDate reminderDay = LocalDate.now().plusDays(2);
        List<TournamentRegistration> candidates =
                registrationRepository.findSoloAddLaterForReminder(reminderDay, reminderDay.plusDays(1));

        int sent = 0;
        for (TournamentRegistration reg : inTournamentOrder(candidates)) {
            try {
                refreshUnderTournamentLock(reg);
                if (!reg.isPairableSolo() || Boolean.TRUE.equals(reg.getSoloReminderSent())) continue;
                String tournamentUrl = baseUrl + "/torneo/" + reg.getTournament().getId();
                String clubName = resolveClubName(reg.getTournament().getClubId());

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
     * Каждые 5 минут — повторное напоминание (с self-service действиями) резервным
     * заявкам без партнёра, если до старта турнира осталось ~3 часа и напоминание ещё
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
        for (TournamentRegistration reg : inTournamentOrder(candidates)) {
            try {
                refreshUnderTournamentLock(reg);
                if (!reg.isPairableSolo() || Boolean.TRUE.equals(reg.getFinalReminderSent())) continue;
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

    private List<TournamentRegistration> inTournamentOrder(List<TournamentRegistration> rows) {
        return rows.stream().sorted(Comparator.comparing(row -> row.getTournament().getId())).toList();
    }

    private void refreshUnderTournamentLock(TournamentRegistration row) {
        tournaments.lockById(row.getTournament().getId()).orElseThrow();
        entities.refresh(row);
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

    // Токен self-service действий (подтвердить/отменить) для резервной заявки без
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
