package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.config.TelegramProperties;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotPoller {

    private static final String UPDATES_URL = "https://api.telegram.org/bot%s/getUpdates?offset=%d";
    private static final String SEND_URL    = "https://api.telegram.org/bot%s/sendMessage";

    private final TelegramProperties properties;
    private final PlayerRepository playerRepository;
    private final RestTemplate restTemplate;

    private final AtomicLong lastUpdateId = new AtomicLong(0);

    @Scheduled(fixedDelay = 5_000)
    @Transactional
    @SuppressWarnings("unchecked")
    public void pollUpdates() {
        if (!properties.isEnabled() || properties.getBotToken() == null || properties.getBotToken().isBlank()) {
            return;
        }

        try {
            String url = String.format(UPDATES_URL, properties.getBotToken(), lastUpdateId.get());
            Map<?, ?> response = restTemplate.getForObject(url, Map.class);

            if (response == null || !Boolean.TRUE.equals(response.get("ok"))) return;

            List<Map<?, ?>> updates = (List<Map<?, ?>>) response.get("result");
            if (updates == null || updates.isEmpty()) return;

            for (Map<?, ?> update : updates) {
                long updateId = ((Number) update.get("update_id")).longValue();
                lastUpdateId.updateAndGet(cur -> Math.max(cur, updateId + 1));

                Map<?, ?> message = (Map<?, ?>) update.get("message");
                if (message == null) continue;

                String text = (String) message.get("text");
                if (text == null || !text.startsWith("/start")) continue;

                Map<?, ?> from = (Map<?, ?>) message.get("from");
                Map<?, ?> chat = (Map<?, ?>) message.get("chat");
                if (from == null || chat == null) continue;

                long chatId = ((Number) chat.get("id")).longValue();
                String username = (String) from.get("username");

                handleStart(chatId, username);
            }
        } catch (Exception e) {
            log.debug("Telegram poll error: {}", e.getMessage());
        }
    }

    private void handleStart(long chatId, String username) {
        if (username == null || username.isBlank()) {
            sendReply(chatId,
                    "Para recibir recordatorios necesitas tener un @username de Telegram " +
                    "guardado en tu perfil de 1-padel.com");
            return;
        }

        String clean = username.startsWith("@") ? username.substring(1) : username;

        Optional<PlayerPadel> found = playerRepository.findByTelegramUsername(clean);
        if (found.isEmpty()) {
            found = playerRepository.findByTelegramUsername("@" + clean);
        }

        if (found.isPresent()) {
            PlayerPadel player = found.get();
            player.setTelegramChatId(chatId);
            playerRepository.save(player);
            log.info("Telegram chat_id={} привязан к игроку id={} ({})", chatId, player.getId(), player.getEmail());
            sendReply(chatId,
                    String.format("¡Hola, <b>%s</b>! ✅\nAhora recibirás recordatorios de torneos directamente acá.",
                            player.getNombre()));
        } else {
            log.warn("Telegram /start от @{} — игрок не найден в базе", username);
            sendReply(chatId,
                    "No encontré tu cuenta en 1-padel.com 😕\n" +
                    "Asegúrate de guardar <b>@" + username + "</b> en tu perfil y volvé a intentarlo.");
        }
    }

    private void sendReply(long chatId, String text) {
        try {
            String url = String.format(SEND_URL, properties.getBotToken());
            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
        } catch (Exception e) {
            log.error("Ошибка при отправке ответа боту в chat_id={}: {}", chatId, e.getMessage());
        }
    }
}
