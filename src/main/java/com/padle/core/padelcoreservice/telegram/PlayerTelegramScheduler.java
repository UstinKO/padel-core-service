package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.persistence.tournament.TournamentRepository;
import com.padle.core.padelcoreservice.service.TelegramParticipationCoordinator;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class PlayerTelegramScheduler {
    private final PlayerTelegramProperties properties;
    private final PlayerTelegramLinks links;
    private final TournamentRepository tournaments;
    private final TelegramParticipationCoordinator participation;
    private final PlayerTelegramNotifications notifications;
    private final PlayerTelegramSummary summary;
    private final PlayerTelegramAccess access;

    @Scheduled(fixedDelay = 60_000)
    public void tick() {
        if (!links.available() || !properties.automaticEnabled()) {
            return;
        }
        var today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        var dates = IntStream.rangeClosed(-1, 4).mapToObj(today::plusDays).toList();
        var now = Instant.now();
        tournaments.findByFechaInicioInAndActive(dates).stream()
                .filter(t -> access.allowsEvent(t.getId()))
                .filter(TelegramParticipationCoordinator::upcoming)
                .filter(t -> Duration.between(now, TelegramParticipationCoordinator.startsAt(t)).toMinutes()
                        <= properties.confirmationHours() * 60L)
                .forEach(t -> {
                    boolean reminder = Duration.between(now, TelegramParticipationCoordinator.startsAt(t)).toMinutes()
                            <= properties.reminderHours() * 60L;
                    participation.prepare(t.getId()).forEach(id -> notifications.send(id, reminder));
                });
    }

    @Scheduled(cron = "${telegram.players.summary-cron:0 0 10 * * *}", zone = "America/Argentina/Buenos_Aires")
    public void daily() {
        summary.daily();
    }
}
