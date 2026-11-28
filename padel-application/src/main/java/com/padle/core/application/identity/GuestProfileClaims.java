package com.padle.core.application.identity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GuestProfileClaims {

    Claim saveRequest(long accountId, String phone);

    Optional<Claim> request(UUID reference, boolean lock);

    Optional<Claim> matchingRequest(long accountId, String phone, boolean lock);

    List<Claim> forAccount(long accountId);

    List<QueueItem> queue(String search, String normalizedPhone, Status status, int limit, long offset);

    List<ProfileChoice> profileChoices(String search, boolean guest);

    long pendingCount();

    Optional<Long> resolved(long guestId);

    void decide(UUID reference, Status status, long ownerId, String reason);

    void approve(UUID reference, long guestId, long accountId, long ownerId, Verification method, boolean independentReview);

    void recordManual(long guestId, Long accountId, long resolvedId, String phone, long ownerId, Verification method);

    void contactResolution(long guestId, ContactChoice choice);

    enum Status { PENDING, NEEDS_DETAILS, MERGED, REJECTED }

    enum Verification { WHATSAPP, IN_PERSON, OTHER }

    enum ContactChoice { KEEP_ACCOUNT, USE_GUEST_PHONE }

    record Claim(UUID reference, long number, long accountId, String phone, Long guestId,
                 Long resolvedPlayerId, Status status, OffsetDateTime createdAt, String reviewReason) {}

    record QueueItem(UUID reference, long number, OffsetDateTime createdAt, long accountId,
                     String accountName, String phone, Status status) {}

    record ProfileChoice(long id, String name) {}
}
