package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.exception.AccountException;
import org.springframework.http.HttpStatus;
import java.util.Locale;
import com.padle.core.padelcoreservice.repository.OwnerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.service.TelegramParticipationCoordinator;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class PlayerTelegramSummary {
    private final PlayerTelegramProperties properties;
    private final PlayerTelegramApi api;
    private final OwnerRepository owners;
    private final TournamentRepository tournaments;
    private final TelegramParticipationCoordinator participation;
    private final PlayerTelegramDelivery delivery;
    private final PlayerTelegramMessages messages;
    private final PlayerTelegramAccess access;

    public void forBot(long userId) {
        if (properties.enabled() && properties.organizerChatId() != null && userId == properties.organizerChatId()) {
            configuredOwner().ifPresent(owner -> send(owner, "manual:" + Instant.now().truncatedTo(ChronoUnit.MINUTES)));
        }
    }

    public void manual(Owner actor) {
        if (!properties.enabled() || !api.available()) {
            throw new AccountException(HttpStatus.CONFLICT, "TELEGRAM_UNAVAILABLE");
        }
        var owner = configuredOwner().filter(o -> o.getId().equals(actor.getId()))
                .orElseThrow(() -> new AccessDeniedException("Сводка Telegram не настроена для этого организатора"));
        send(owner, "manual:" + Instant.now().truncatedTo(ChronoUnit.MINUTES));
    }

    public void daily() {
        if (properties.enabled() && properties.dailySummaryEnabled()) {
            configuredOwner().ifPresent(owner -> send(owner, "daily:" + LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"))));
        }
    }

    private Optional<Owner> configuredOwner() {
        return Optional.ofNullable(properties.organizerChatId()).filter(id -> id > 0)
                .flatMap(id -> Optional.ofNullable(properties.organizerOwnerId()))
                .flatMap(owners::findById).filter(Owner::isSuperAdmin).filter(Owner::isEnabled);
    }

    private void send(Owner owner, String cycle) {
        var today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        var dates = IntStream.range(0, 8).mapToObj(today::plusDays).toList();
        var events = tournaments.findByFechaInicioInAndActive(dates).stream()
                .filter(t -> access.allowsEvent(t.getId()))
                .filter(TelegramParticipationCoordinator::upcoming).toList();
        if (events.isEmpty()) {
            delivery.sendOnce("summary:" + cycle + ":empty", null, null, properties.organizerChatId(),
                    messages.text("summary.empty", Locale.forLanguageTag("ru")), List.of());
        }
        events.forEach(event -> {
            var summary = participation.summary(event.getId(), owner);
            delivery.sendOnce("summary:" + cycle + ":" + event.getId(), null, null,
                    properties.organizerChatId(), format(summary), List.of());
        });
    }

    String format(Summary summary) {
        var rows = summary.participants();
        long confirmed = rows.stream().filter(r -> "CONFIRMED".equals(r.response())).count();
        long missing = rows.stream().filter(r -> !r.connected()).count();
        long errors = rows.stream().filter(r -> List.of("FAILED", "BLOCKED", "UNKNOWN").contains(r.delivery())).count();
        String pending = rows.stream().filter(r -> !"CONFIRMED".equals(r.response())).limit(20)
                .map(r -> r.name().substring(0, Math.min(r.name().length(), 100))).collect(Collectors.joining(", "));
        String manual = rows.stream().filter(Recipient::manualContact).limit(20)
                .map(r -> r.name().substring(0, Math.min(r.name().length(), 100))
                        + (r.preference() == ContactPreference.WHATSAPP ? " (WhatsApp)" : ""))
                .collect(Collectors.joining(", "));
        return messages.text("summary.event", Locale.forLanguageTag("ru"), summary.name(), summary.tournamentId(),
                rows.size(), confirmed, rows.size() - confirmed, missing, errors, pending.isBlank() ? "—" : pending)
                + "\n" + messages.text("summary.manual", Locale.forLanguageTag("ru"), manual.isBlank() ? "—" : manual);
    }
}
