package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.config.TelegramProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TelegramService {

    private static final String API_URL = "https://api.telegram.org/bot%s/sendMessage";

    private final TelegramProperties properties;
    private final RestTemplate restTemplate;

    public TelegramService(TelegramProperties properties,
                           @Qualifier("telegramRestTemplate") RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    /** Отправить admin-алерт (ошибки, атаки) в основной чат. */
    public void sendMessage(String text) {
        sendToChat(properties.getChatId(), properties.getThreadId(), text, null);
    }

    /** Отправить admin-алерт с кнопками в основной чат. */
    public void sendMessageWithButtons(String text, List<List<UrlButton>> keyboard) {
        sendToChat(properties.getChatId(), properties.getThreadId(), text, keyboard);
    }

    /** Отправить личное сообщение конкретному игроку по его chat_id. */
    public void sendDm(Long chatId, String text, List<List<UrlButton>> keyboard) {
        if (chatId == null) return;
        sendToChat(String.valueOf(chatId), null, text, keyboard);
    }

    private void sendToChat(String chatId, Integer threadId, String text, List<List<UrlButton>> keyboard) {
        if (!properties.isEnabled()) {
            log.warn("Telegram отключён (telegram.enabled=false) — сообщение не отправлено");
            return;
        }
        if (properties.getBotToken() == null || properties.getBotToken().isBlank()) {
            log.warn("TELEGRAM_BOT_TOKEN не задан — сообщение не отправлено");
            return;
        }
        if (chatId == null || chatId.isBlank()) {
            log.warn("chat_id не задан — сообщение не отправлено");
            return;
        }

        try {
            String url = String.format(API_URL, properties.getBotToken());

            Map<String, Object> body = new HashMap<>();
            body.put("chat_id", chatId);
            body.put("text", text);
            body.put("parse_mode", "HTML");
            if (threadId != null) {
                body.put("message_thread_id", threadId);
            }
            if (keyboard != null && !keyboard.isEmpty()) {
                body.put("reply_markup", buildKeyboard(keyboard));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.postForObject(url, request, Map.class);
            log.info("Telegram ✓ отправлено в chat_id={}", chatId);
        } catch (Exception e) {
            log.error("Telegram ✗ ошибка отправки в chat_id={}: {}", chatId, e.getMessage());
        }
    }

    public boolean isEnabled() {
        return properties.isEnabled()
                && properties.getBotToken() != null && !properties.getBotToken().isBlank()
                && properties.getChatId() != null && !properties.getChatId().isBlank();
    }

    public String getChatId() { return properties.getChatId(); }
    public Integer getThreadId() { return properties.getThreadId(); }

    private Map<String, Object> buildKeyboard(List<List<UrlButton>> rows) {
        List<List<Map<String, String>>> keyboard = new ArrayList<>();
        for (List<UrlButton> row : rows) {
            List<Map<String, String>> buttons = new ArrayList<>();
            for (UrlButton btn : row) {
                Map<String, String> b = new HashMap<>();
                b.put("text", btn.text());
                b.put("url", btn.url());
                buttons.add(b);
            }
            keyboard.add(buttons);
        }
        Map<String, Object> markup = new HashMap<>();
        markup.put("inline_keyboard", keyboard);
        return markup;
    }

    public record UrlButton(String text, String url) {}
}
