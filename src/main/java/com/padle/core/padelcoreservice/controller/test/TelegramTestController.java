package com.padle.core.padelcoreservice.controller.test;

import com.padle.core.padelcoreservice.logging.TelegramAlertQueue;
import com.padle.core.padelcoreservice.security.RateLimitFilter;
import com.padle.core.padelcoreservice.service.TelegramReminderScheduler;
import com.padle.core.padelcoreservice.service.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/test/telegram")
@RequiredArgsConstructor
public class TelegramTestController {

    private final TelegramService telegramService;
    private final TelegramReminderScheduler reminderScheduler;

    // GET /test/telegram/status — показать состояние конфига Telegram
    @GetMapping("/status")
    public ResponseEntity<String> status() {
        String info = String.format(
                "enabled=%s | chatId=%s | threadId=%s | tokenPresent=%s",
                telegramService.isEnabled(),
                telegramService.getChatId(),
                telegramService.getThreadId(),
                "checked via isEnabled()");
        return ResponseEntity.ok(info);
    }

    // POST /test/telegram/ping — отправить тестовое сообщение
    @PostMapping("/ping")
    public ResponseEntity<String> ping() {
        telegramService.sendMessage("🔔 <b>Test</b>: Telegram-интеграция работает! Сервис E-Padel подключён.");
        return ResponseEntity.ok("Сообщение отправлено");
    }

    // POST /test/telegram/reminders — запустить напоминания (турниры на ближайшие 3 часа)
    @PostMapping("/reminders")
    public ResponseEntity<String> triggerReminders() {
        reminderScheduler.sendTomorrowReminders();
        return ResponseEntity.ok("Напоминания отправлены (турниры в окне +2ч45м..+3ч15м)");
    }

    // POST /test/telegram/error — имитировать ошибку → должна прийти в Telegram
    @PostMapping("/error")
    public ResponseEntity<String> triggerError() {
        log.error("TEST: Ошибка из TelegramTestController — проверка интеграции с Telegram-логами");
        // Сразу сбрасываем очередь не дожидаясь 30-секундного cron
        reminderScheduler.flushErrorAlerts();
        return ResponseEntity.ok("Ошибка залогирована и отправлена в Telegram");
    }

    // POST /test/telegram/reminders/force/{tournamentId} — принудительно отправить напоминание для конкретного турнира
    @PostMapping("/reminders/force/{tournamentId}")
    public ResponseEntity<String> triggerReminderForTournament(@PathVariable Long tournamentId) {
        reminderScheduler.sendReminderByTournamentId(tournamentId);
        return ResponseEntity.ok("Напоминание отправлено для турнира " + tournamentId);
    }

    // POST /test/telegram/attacks — имитировать сводку атак
    @PostMapping("/attacks")
    public ResponseEntity<String> triggerAttackSummary() {
        // Имитируем 42 заблокированных запроса для теста
        RateLimitFilter.BLOCKED_TODAY.set(42);
        reminderScheduler.sendDailyAttackSummary();
        return ResponseEntity.ok("Сводка атак отправлена (42 блокировки — тестовые данные)");
    }
}
