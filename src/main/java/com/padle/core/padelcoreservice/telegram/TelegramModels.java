package com.padle.core.padelcoreservice.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class TelegramModels {
    private TelegramModels() {}
    public record Connection(long playerId, long userId, long chatId, boolean enabled) {}
    public enum ContactPreference { UNSPECIFIED, TELEGRAM, WHATSAPP }
    public record PreferenceChange(@NotNull ContactPreference preference) {}
    public record LinkStatus(boolean available, boolean connected, ContactPreference preference) {}
    public record Link(String url, OffsetDateTime expiresAt) {}
    public record Recipient(long registrationId, long playerId, String name, String response,
                            boolean connected, String delivery, ContactPreference preference) {
        @JsonProperty("manualContact")
        public boolean manualContact() { return !connected; }
    }
    public record Summary(long tournamentId, String name, List<Recipient> participants) {}
    public record Delivery(UUID id, String key, long registrationId, String state) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiResponse<T>(boolean ok, T result, @JsonProperty("error_code") Integer errorCode) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(long id) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(long id, String type) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(@JsonProperty("message_id") long messageId, User from, Chat chat, String text) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Callback(String id, User from, Message message, String data) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Update(@JsonProperty("update_id") long updateId, Message message,
                         @JsonProperty("callback_query") Callback callback) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Button(String text, String url, @JsonProperty("callback_data") String callbackData) {
        public static Button link(String text, String url) { return new Button(text, url, null); }
        public static Button action(String text, String data) { return new Button(text, null, data); }
    }
    public record Keyboard(@JsonProperty("inline_keyboard") List<List<Button>> rows) {}
    public record Send(@JsonProperty("chat_id") long chatId, String text,
                       @JsonProperty("reply_markup") Keyboard keyboard) {}
    public record Acknowledge(@JsonProperty("callback_query_id") String id, String text) {}
    public record Result(String state, Long messageId) {}
}
