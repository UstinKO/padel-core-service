package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.validation.NotDisposableEmail;
import com.padle.core.padelcoreservice.validation.ValidDomain;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class GuestProfileClaimDtos {
    private GuestProfileClaimDtos() {}

    public record Request(@NotBlank @Size(max = 32) String phone) {}

    public enum Status { PENDING, NEEDS_DETAILS, MERGED, REJECTED }
    public enum Verification { WHATSAPP, IN_PERSON, OTHER }
    public enum Decision { NEEDS_DETAILS, REJECTED }

    public record Approval(@Size(max = 32) String whatsappSenderPhone, Boolean verified,
                           Verification verificationMethod, @Positive Long guestId, boolean replacePhone) {}

    public record ManualMerge(@Positive long guestId, @Positive long accountId, @NotNull Boolean verified,
                              @NotNull Verification verificationMethod, @Size(max = 32) String whatsappSenderPhone,
                              boolean replacePhone) {}

    public record ReviewDecision(@NotNull Decision decision) {}

    public record Activation(
            @NotBlank @Size(max = 32) String whatsappSenderPhone,
            @NotBlank @Size(max = 150) @Email @ValidDomain @NotDisposableEmail String email) {}

    public record RequestReceipt(UUID reference, long number, String whatsappUrl, String playerName,
                                 Status status, OffsetDateTime createdAt) {}

    public record QueueItem(UUID reference, long number, OffsetDateTime createdAt, long accountId,
                            String accountName, String phone, Status status) {}

    public record Queue(long pendingCount, List<QueueItem> items, boolean hasMore) {}

    public record ProfileChoice(long id, String name) {}

    public record Profile(long id, String name, String email, String phone, String telegram, String level,
                          long registrations, long results) {}

    public record Review(long accountId, String accountEmail, boolean approved, long number, Status status,
                         String submittedPhone, Profile account, Profile guest, List<String> conflicts,
                         List<String> differences, String reviewReason) {}

    public record Result(long playerId) {}
}
