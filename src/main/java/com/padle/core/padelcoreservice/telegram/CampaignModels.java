package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.telegram.TelegramAudienceModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class CampaignModels {
    private CampaignModels() {}
    public record Texts(@Size(max = 4096) String es, @Size(max = 4096) String ru, @Size(max = 4096) String en) {
        public Texts {
            es = Objects.requireNonNullElse(es, "");
            ru = Objects.requireNonNullElse(ru, "");
            en = Objects.requireNonNullElse(en, "");
        }
        public static Texts empty() { return new Texts("", "", ""); }
        public boolean localized() { return !es.isBlank() || !ru.isBlank() || !en.isBlank(); }
        public String forLanguage(String language) {
            return switch (language) { case "ru" -> ru; case "en" -> en; default -> es; };
        }
    }
    public record Prepare(@NotNull UUID requestId, @NotNull @Valid Selection selection, @Valid Texts texts) {
        public Prepare(UUID requestId, Selection selection) { this(requestId, selection, null); }
        public Texts versions() { return Objects.requireNonNullElseGet(texts, Texts::empty); }
    }
    public record Confirm(@AssertTrue boolean confirmed) {}
    public record Action(@NotNull UUID requestId, @AssertTrue boolean confirmed) {}
    public record Prepared(UUID campaignId, String text, List<Candidate> recipients, List<Candidate> unavailable,
                           int selectedCount, boolean sendingAvailable, boolean canTest, OffsetDateTime expiresAt, Texts texts) {}
    public record Recipient(long playerId, String name, String state, String reason, int attempt, String language, String text) {}
    public record View(UUID id, Long tournamentId, String author, String text, String kind, String state,
                       OffsetDateTime createdAt, List<Recipient> recipients, boolean canConfirm, boolean canRetry, Texts texts) {}
    public record Header(UUID id, long ownerId, Long tournamentId, String author, String text, String kind,
                         String state, String fingerprint, OffsetDateTime createdAt, OffsetDateTime expiresAt, Texts texts) {}
    public record Creation(Header header, boolean created) {}
    public record Link(long playerId, long chatId, OffsetDateTime connectedAt) {}
    public record Dispatch(UUID campaignId, long playerId, int attempt) {
        public String key() { return "campaign:" + campaignId + ":" + playerId + ":" + attempt; }
    }
    public record StoredRecipient(long playerId, String name, String category, String groups, Long chatId,
                                  OffsetDateTime connectedAt, String state, String reason, int attempt, String language, String text) {}
}
