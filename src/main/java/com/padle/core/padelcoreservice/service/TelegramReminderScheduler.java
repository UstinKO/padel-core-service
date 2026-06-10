package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.logging.TelegramAlertQueue;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramReminderScheduler {

    private final TelegramService telegramService;
    private final TournamentRepository tournamentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final ClubRepository clubRepository;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // Защита от дублей: tournament_id → дата отправки напоминания
    private final Map<Long, LocalDate> sentReminders = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────
    // 1. НАПОМИНАНИЯ ЗА 3 ЧАСА — каждые 30 минут
    // ─────────────────────────────────────────────────────────────
    @Scheduled(cron = "0 0/30 * * * *")
    public void sendTomorrowReminders() {
        // Очищаем устаревшие записи (вчерашние)
        sentReminders.entrySet().removeIf(e -> e.getValue().isBefore(LocalDate.now()));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.plusHours(2).plusMinutes(45);
        LocalDateTime windowEnd   = now.plusHours(3).plusMinutes(15);

        // HashSet: дедуплицирует если обе даты одинаковые (окно не пересекает полночь)
        Set<LocalDate> datesToCheck = new java.util.HashSet<>();
        datesToCheck.add(windowStart.toLocalDate());
        datesToCheck.add(windowEnd.toLocalDate());

        List<Tournament> candidates = tournamentRepository.findByFechaInicioInAndActive(datesToCheck);

        List<Tournament> upcoming = candidates.stream()
                .filter(t -> {
                    LocalDateTime start = LocalDateTime.of(t.getFechaInicio(), t.getHoraInicio());
                    return !start.isBefore(windowStart) && start.isBefore(windowEnd);
                })
                .filter(t -> !sentReminders.containsKey(t.getId()))
                .collect(Collectors.toList());

        if (upcoming.isEmpty()) return;

        log.info("Отправка Telegram-напоминаний за 3 часа: {} турниров", upcoming.size());

        for (Tournament tournament : upcoming) {
            try {
                sendReminderForTournament(tournament);
                sentReminders.put(tournament.getId(), LocalDate.now());
            } catch (Exception e) {
                log.error("Ошибка при отправке напоминания для турнира {}: {}", tournament.getId(), e.getMessage());
            }
        }
    }

    /** Вызывается из тестового контроллера — отправить напоминание для конкретного турнира. */
    public void sendReminderByTournamentId(Long tournamentId) {
        tournamentRepository.findById(tournamentId).ifPresent(t -> {
            sendReminderForTournament(t);
            sentReminders.put(t.getId(), LocalDate.now());
        });
    }

    private void sendReminderForTournament(Tournament tournament) {
        String clubName = clubRepository.findById(tournament.getClubId())
                .map(Club::getNombre)
                .orElse("el club");
        String timeStr = tournament.getHoraInicio().format(DateTimeFormatter.ofPattern("HH:mm"));

        // Подтверждённые игроки с Telegram-юзернеймом
        List<TournamentRegistration> confirmed = registrationRepository
                .findByTournamentIdAndStatus(tournament.getId(), RegistrationStatus.CONFIRMED);

        String mentions = confirmed.stream()
                .map(r -> r.getPlayer().getTelegramUsername())
                .filter(u -> u != null && !u.isBlank())
                .map(u -> u.startsWith("@") ? u : "@" + u)
                .collect(Collectors.joining(" "));

        StringBuilder text = new StringBuilder();
        text.append(String.format(
                "🎾 ¡Faltan 3 horas! Torneo <b>%s</b>\n" +
                "📍 %s  •  🕐 %s hs.\n" +
                "Les pedimos llegar 15 minutos antes.",
                HtmlUtils.htmlEscape(tournament.getNombre()),
                HtmlUtils.htmlEscape(clubName),
                timeStr));

        if (!mentions.isBlank()) {
            text.append("\n\n👥 ").append(mentions);
        }

        String tournamentUrl = baseUrl + "/torneo/" + tournament.getId();

        List<List<TelegramService.UrlButton>> keyboard = List.of(
                List.of(
                        new TelegramService.UrlButton("🎾 Ver el torneo", tournamentUrl),
                        new TelegramService.UrlButton("❌ Cancelar inscripción", tournamentUrl)
                )
        );

        // Личные DM каждому подтверждённому игроку у кого есть telegram_chat_id (написал /start боту)
        int dmSent = 0;
        for (TournamentRegistration reg : confirmed) {
            Long playerChatId = reg.getPlayer().getTelegramChatId();
            if (playerChatId != null) {
                telegramService.sendDm(playerChatId, text.toString(), keyboard);
                dmSent++;
            }
        }
        log.info("Напоминание для турнира {}: {} из {} игроков получили DM",
                tournament.getId(), dmSent, confirmed.size());
    }

    // ─────────────────────────────────────────────────────────────
    // 2. СБРОС ОШИБОК ИЗ ЛОГОВ — каждые 30 секунд
    // ─────────────────────────────────────────────────────────────
    @Scheduled(fixedDelay = 30_000)
    public void flushErrorAlerts() {
        if (TelegramAlertQueue.QUEUE.isEmpty()) return;

        List<String> errors = new ArrayList<>();
        TelegramAlertQueue.QUEUE.drainTo(errors, 5); // максимум 5 за раз

        if (errors.isEmpty()) return;

        int total = errors.size();
        StringBuilder msg = new StringBuilder();
        msg.append(String.format("🚨 <b>Errores de aplicación</b> (%d)\n━━━━━━━━━━━━━━━━\n", total));
        for (int i = 0; i < errors.size(); i++) {
            msg.append(i + 1).append(". ").append(errors.get(i)).append("\n");
        }

        // Если в очереди ещё остались ошибки — сигнализируем
        int remaining = TelegramAlertQueue.QUEUE.size();
        if (remaining > 0) {
            msg.append(String.format("\n<i>+%d errores más en cola</i>", remaining));
        }

        telegramService.sendMessage(msg.toString().trim());
    }

    // ─────────────────────────────────────────────────────────────
    // 3. ЕЖЕДНЕВНАЯ СВОДКА АТАК — в 23:55 (UTC-3 → 02:55 UTC)
    // ─────────────────────────────────────────────────────────────
    @Scheduled(cron = "0 55 2 * * *")
    public void sendDailyAttackSummary() {
        int blocked = RateLimitFilter.BLOCKED_TODAY.getAndSet(0); // сброс счётчика

        String icon = blocked == 0 ? "✅" : (blocked < 50 ? "⚠️" : "🔴");
        String msg = String.format(
                "%s <b>Resumen diario de seguridad</b>\n" +
                "━━━━━━━━━━━━━━━━\n" +
                "🛡 Solicitudes bloqueadas: <b>%d</b>\n" +
                "📅 %s",
                icon, blocked,
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        );

        telegramService.sendMessage(msg);
        log.info("Telegram: отправлена дневная сводка атак (blocked={})", blocked);
    }
}
