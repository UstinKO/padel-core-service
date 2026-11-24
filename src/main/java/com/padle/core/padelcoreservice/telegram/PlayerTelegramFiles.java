package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.receipts.ReceiptFiles;
import com.padle.core.padelcoreservice.receipts.ReceiptModels.File;
import com.padle.core.padelcoreservice.telegram.TelegramModels.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class PlayerTelegramFiles {
    private final PlayerTelegramProperties properties;
    private final RestTemplate http;

    public PlayerTelegramFiles(PlayerTelegramProperties properties,
                               @Qualifier("telegramRestTemplate") RestTemplate http) {
        this.properties = properties;
        this.http = http;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TelegramFile(@JsonProperty("file_path") String path, @JsonProperty("file_size") Long size) {}
    public record Request(@JsonProperty("file_id") String id) {}

    public File download(String fileId) {
        try {
            var response = http.exchange("https://api.telegram.org/bot" + properties.botToken() + "/getFile",
                    HttpMethod.POST, new HttpEntity<>(new Request(fileId)),
                    new ParameterizedTypeReference<ApiResponse<TelegramFile>>() {}).getBody();
            if (response == null || !response.ok() || response.result() == null) {
                throw new RestClientException("Файл временно недоступен");
            }
            var file = response.result();
            if (file.path() == null || !file.path().matches("[A-Za-z0-9_/-]+\\.[A-Za-z0-9]+")
                    || file.path().contains("..") || file.path().startsWith("/")
                    || (file.size() != null && file.size() > ReceiptFiles.MAX_BYTES)) {
                throw ReceiptFiles.invalid();
            }
            return read(file.path());
        } catch (RestClientException error) {
            // Исключение HTTP содержит токен. Оно не передаётся в журнал или ответ API.
            throw new RestClientException("Файл временно недоступен");
        }
    }

    private File read(String path) {
        var bytes = http.execute("https://api.telegram.org/file/bot" + properties.botToken() + "/" + path,
                HttpMethod.GET, null, response -> {
                    if (response.getHeaders().getContentLength() > ReceiptFiles.MAX_BYTES) {
                        throw ReceiptFiles.invalid();
                    }
                    return response.getBody().readNBytes(ReceiptFiles.MAX_BYTES + 1);
                });
        if (bytes == null) {
            throw ReceiptFiles.invalid();
        }
        return ReceiptFiles.validate(bytes);
    }
}
