package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

@Service
@RequiredArgsConstructor
public class PlayerTelegramDelivery {
    private final PlayerTelegramStore store;
    private final PlayerTelegramApi api;
    private final Map<Long, Long> lastSent = new HashMap<>();
    private long nextSend;

    public void sendOnce(String key, Long tournamentId, Long registrationId,
                         long chatId, String text, List<List<Button>> buttons) {
        if (!api.available() || !store.claim(key, tournamentId, registrationId, chatId)) {
            return;
        }
        var result = pacedSend(chatId, text, buttons);
        store.finish(key, result);
        if (result.state().equals("BLOCKED")) {
            store.byUser(chatId).ifPresent(connection -> store.disconnect(connection.playerId()));
        }
    }
    private synchronized Result pacedSend(long chatId, String text, List<List<Button>> buttons) {
        long now = System.nanoTime();
        long due = Math.max(nextSend, lastSent.getOrDefault(chatId, now - 1_000_000_000L) + 1_000_000_000L);
        while (due > System.nanoTime() && !Thread.currentThread().isInterrupted()) {
            LockSupport.parkNanos(due - System.nanoTime());
        }
        if (Thread.currentThread().isInterrupted()) {
            return new Result("UNKNOWN", null);
        }
        nextSend = System.nanoTime() + 100_000_000L;
        lastSent.put(chatId, System.nanoTime());
        lastSent.entrySet().removeIf(entry -> entry.getValue() < System.nanoTime() - 60_000_000_000L);
        return api.send(chatId, text, buttons);
    }
}
