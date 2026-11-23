package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.dto.ParticipationDtos.Decision;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.service.TournamentParticipationService;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlayerTelegramPoller {
    private final PlayerTelegramApi api;
    private final PlayerTelegramStore store;
    private final PlayerTelegramLinks links;
    private final PlayerRepository players;
    private final PlayerTelegramMessages messages;
    private final PlayerTelegramDelivery delivery;
    private final TournamentParticipationService participation;
    private final PlayerTelegramSummary summary;

    public void poll() {
        if (!api.available()) {
            return;
        }
        UUID lease = UUID.randomUUID();
        if (!store.acquirePoll(lease)) {
            return;
        }
        try {
            for (var update : api.updates(store.offset())) {
                if (!store.advance(lease, store.offset())) {
                    return;
                }
                handle(update);
                if (!store.advance(lease, update.updateId() + 1)) {
                    return;
                }
            }
        } catch (RestClientException exception) {
            // URI исключения содержит токен бота и не должен попадать в журнал.
            log.warn("Telegram: временно недоступно чтение обновлений");
        } finally {
            store.release(lease);
        }
    }

    void handle(Update update) {
        Optional.ofNullable(update.message()).filter(m -> privateChat(m.chat(), m.from()))
                .ifPresent(message -> command(update.updateId(), message));
        Optional.ofNullable(update.callback()).filter(c -> c.message() != null)
                .filter(c -> privateChat(c.message().chat(), c.from())).ifPresent(this::confirm);
    }

    private boolean privateChat(Chat chat, User user) {
        return chat != null && user != null && "private".equals(chat.type()) && chat.id() == user.id() && user.id() > 0;
    }

    private void command(long updateId, Message message) {
        String text = Optional.ofNullable(message.text()).orElse("").strip();
        if (text.equals("/stop")) {
            store.byUser(message.from().id()).ifPresent(c -> store.disconnect(c.playerId()));
            reply(updateId, message, "stopped", Locale.forLanguageTag("es"));
        } else if (text.startsWith("/start ")) {
            var linked = links.bind(text.substring(7).strip(), message.from().id(), message.chat().id());
            var locale = linked.flatMap(players::findById).map(p -> p.getLocale()).orElse(Locale.forLanguageTag("es"));
            reply(updateId, message, linked.isPresent() ? "linked" : "invalidLink", locale);
        } else if (text.equals("/summary")) {
            summary.forBot(message.from().id());
        } else if (text.equals("/start") || text.equals("/help")) {
            var locale = store.byUser(message.from().id()).flatMap(c -> players.findById(c.playerId()))
                    .map(p -> p.getLocale()).orElse(Locale.forLanguageTag("es"));
            reply(updateId, message, "help", locale);
        }
    }

    private void reply(long updateId, Message message, String key, Locale locale) {
        delivery.sendOnce("reply:" + updateId, null, null, message.chat().id(), messages.text(key, locale), List.of());
    }

    private void confirm(Callback callback) {
        if (callback.data() == null || !callback.data().startsWith("c:")) {
            return;
        }
        var player = store.byUser(callback.from().id()).flatMap(c -> players.findById(c.playerId()))
                .filter(p -> p.isActivo());
        var locale = player.map(p -> p.getLocale()).orElse(Locale.forLanguageTag("es"));
        String response = messages.text("unavailable", locale);
        try {
            var parts = callback.data().split(":", -1);
            if (parts.length != 3) {
                api.acknowledge(callback.id(), response);
                return;
            }
            var id = UUID.fromString(parts[1]);
            long expectedStart = Long.parseLong(parts[2], 36);
            if (player.isPresent()) {
                var review = participation.confirmTelegram(id, player.orElseThrow(), expectedStart);
                response = messages.text(review.status().name().equals("CONFIRMED") && !review.outdated()
                        ? "confirmed" : "unavailable", locale);
            }
        } catch (AccountException | IllegalArgumentException exception) {
            response = messages.text("unavailable", locale);
        }
        api.acknowledge(callback.id(), response);
    }
}
