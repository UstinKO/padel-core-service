package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Optional;

@Component
public class PlayerTelegramApi {
    private final PlayerTelegramProperties properties;
    private final RestTemplate http;

    public PlayerTelegramApi(PlayerTelegramProperties properties,
                             @Qualifier("telegramRestTemplate") RestTemplate http) {
        this.properties = properties;
        this.http = http;
    }

    public boolean available() {
        return properties.enabled() && properties.botToken() != null && !properties.botToken().isBlank();
    }

    public List<Update> updates(long offset) {
        var response = http.exchange(url("getUpdates") + "?offset=" + offset + "&limit=20&timeout=0",
                HttpMethod.GET, HttpEntity.EMPTY, new ParameterizedTypeReference<ApiResponse<List<Update>>>() {}).getBody();
        if (response == null || !response.ok() || response.result() == null) {
            throw new RestClientException("Telegram не подтвердил чтение обновлений");
        }
        return response.result();
    }

    public Result send(long chatId, String text, List<List<Button>> buttons) {
        try {
            var body = new Send(chatId, text, new Keyboard(buttons));
            var response = http.exchange(url("sendMessage"), HttpMethod.POST, new HttpEntity<>(body),
                    new ParameterizedTypeReference<ApiResponse<Message>>() {}).getBody();
            return Optional.ofNullable(response).filter(ApiResponse::ok).map(ApiResponse::result)
                    .filter(message -> message.messageId() > 0)
                    .map(message -> new Result("SENT", message.messageId()))
                    .orElseGet(() -> new Result("FAILED", null));
        } catch (HttpStatusCodeException exception) {
            String state = exception.getStatusCode().value() == 403 ? "BLOCKED"
                    : exception.getStatusCode().is4xxClientError() ? "FAILED" : "UNKNOWN";
            return new Result(state, null);
        } catch (RestClientException exception) {
            // Ответ мог потеряться после принятия сообщения Telegram: повтор не выполняется.
            return new Result("UNKNOWN", null);
        }
    }

    public void acknowledge(String callbackId, String text) {
        try {
            http.postForEntity(url("answerCallbackQuery"), new Acknowledge(callbackId, text), String.class);
        } catch (RestClientException exception) {
            // Сохранённое решение участника не зависит от подтверждения всплывающей подсказки.
        }
    }

    private String url(String method) {
        return "https://api.telegram.org/bot" + properties.botToken() + "/" + method;
    }
}
