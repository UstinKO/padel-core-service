package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.telegram.TelegramAudienceModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CampaignModels {
    private CampaignModels() {}
    public record Prepare(@NotNull UUID requestId, @NotNull @Valid Selection selection) {}
    public record Confirm(@AssertTrue boolean confirmed) {}
    public record Action(@NotNull UUID requestId, @AssertTrue boolean confirmed) {}
    public record Prepared(UUID campaignId, String text, List<Candidate> recipients, List<Candidate> unavailable,
                           int selectedCount, boolean sendingAvailable, boolean canTest, OffsetDateTime expiresAt) {}
    public record Recipient(long playerId, String name, String state, String reason, int attempt) {}
    public record View(UUID id, Long tournamentId, String author, String text, String kind, String state,
                       OffsetDateTime createdAt, List<Recipient> recipients, boolean canConfirm, boolean canRetry) {}
    public record Header(UUID id, long ownerId, Long tournamentId, String author, String text, String kind,
                         String state, String fingerprint, OffsetDateTime createdAt, OffsetDateTime expiresAt) {}
    public record Creation(Header header, boolean created) {}
    public record Link(long playerId, long chatId, OffsetDateTime connectedAt) {}
    public record Dispatch(UUID campaignId, long playerId, int attempt) {
        public String key() { return "campaign:" + campaignId + ":" + playerId + ":" + attempt; }
    }
    public record StoredRecipient(long playerId, String name, String category, String groups, Long chatId,
                                  OffsetDateTime connectedAt, String state, String reason, int attempt) {}
}
