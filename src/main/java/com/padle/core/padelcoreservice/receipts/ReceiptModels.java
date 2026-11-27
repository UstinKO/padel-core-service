package com.padle.core.padelcoreservice.receipts;

import com.padle.core.application.registration.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class ReceiptModels {
    private ReceiptModels() {}
    public enum State { PENDING, ACCEPTED, REJECTED }
    public record Receipt(UUID id, long registrationId, long registrationVersion, long playerId,
                          String playerName, String eventName, String contentType, State state,
                          OffsetDateTime createdAt, OffsetDateTime reviewedAt, String rejectionReason,
                          Long paymentId, boolean currentCycle) {}
    public record Upload(UUID id, long playerId, long chatId, String fileId, UUID receiptId, boolean expired) {}
    public record File(String contentType, byte[] content) {}
    public record Accept(@NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount,
                         @NotBlank @Pattern(regexp = "ARS|USD|USDT") String currency,
                         @NotNull PaymentMethod method) {}
    public record Reject(@NotBlank @Size(max = 500) String reason) {}
}
