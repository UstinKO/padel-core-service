package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LFPT-443: email-напоминания за 5 часов до начала индивидуальных турниров
 * (AMERICANO, KING_OF_COURT) и Cancha Abierta — с разным текстом письма для каждой группы.
 * Дедуп — персистентный (Tournament.startReminderSentAt), по образцу
 * PairTournamentReminderScheduler (LFPT-437).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndividualTournamentReminderScheduler {

    private static final Set<TournamentType> INDIVIDUAL_TYPES =
            EnumSet.of(TournamentType.AMERICANO, TournamentType.KING_OF_COURT);

    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final ClubRepository clubRepository;
    private final EmailService emailService;

    @Scheduled(cron = "0 0/30 * * * *")
    @Transactional
    public void sendIndividualTournamentReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.plusHours(4).plusMinutes(45);
        LocalDateTime windowEnd = now.plusHours(5).plusMinutes(15);

        Set<LocalDate> datesToCheck = new HashSet<>();
        datesToCheck.add(windowStart.toLocalDate());
        datesToCheck.add(windowEnd.toLocalDate());

        List<Tournament> candidates = tournamentRepository.findByFechaInicioInAndActive(datesToCheck);

        List<Tournament> upcoming = candidates.stream()
                .filter(t -> INDIVIDUAL_TYPES.contains(t.getTipo()) || t.getTipo() == TournamentType.CANCHA_ABIERTA)
                .filter(t -> t.getStartReminderSentAt() == null)
                .filter(t -> {
                    LocalDateTime start = LocalDateTime.of(t.getFechaInicio(), t.getHoraInicio());
                    return !start.isBefore(windowStart) && start.isBefore(windowEnd);
                })
                .collect(Collectors.toList());

        if (upcoming.isEmpty()) return;

        log.info("Отправка email-напоминаний за 5 часов до индивидуального турнира / Cancha Abierta: {} турниров",
                upcoming.size());

        for (Tournament tournament : upcoming) {
            try {
                sendReminderForTournament(tournament);
            } catch (Exception e) {
                log.error("Ошибка при отправке email-напоминания для турнира {}: {}",
                        tournament.getId(), e.getMessage(), e);
            } finally {
                // Помечаем турнир обработанным в любом случае (в т.ч. без CONFIRMED-игроков) —
                // защита от дублей приоритетнее повторной попытки после частичной ошибки.
                tournament.setStartReminderSentAt(LocalDateTime.now());
                tournamentRepository.save(tournament);
            }
        }
    }

    private void sendReminderForTournament(Tournament tournament) {
        Club club = clubRepository.findById(tournament.getClubId()).orElse(null);
        String clubName = club != null ? club.getNombre() : "el club";
        String direccion = club != null ? club.getDireccion() : "";
        String hora = tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));
        boolean canchaAbierta = tournament.getTipo() == TournamentType.CANCHA_ABIERTA;

        List<TournamentRegistration> confirmed = registrationRepository
                .findByTournamentIdAndStatus(tournament.getId(), RegistrationStatus.CONFIRMED);

        int sent = 0;
        for (TournamentRegistration registration : confirmed) {
            String email = registration.getPlayer().getEmail();
            if (email == null || email.isBlank()) {
                log.warn("Игрок {} без email — пропускаем напоминание о турнире {}",
                        registration.getPlayer().getId(), tournament.getId());
                continue;
            }
            if (canchaAbierta) {
                emailService.sendCanchaAbiertaReminderEmail(
                        email, registration.getPlayer().getNombre(), clubName, direccion, hora);
            } else {
                emailService.sendIndividualTournamentReminderEmail(
                        email, registration.getPlayer().getNombre(), clubName, direccion, hora);
            }
            sent++;
        }
        log.info("Напоминание для турнира {}: письмо отправлено {} из {} подтверждённых игроков",
                tournament.getId(), sent, confirmed.size());
    }
}
