package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.validation.NotDisposableEmail;
import com.padle.core.padelcoreservice.validation.ValidDomain;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class GuestProfileClaimDtos {
    private GuestProfileClaimDtos() {}

    public record Request(@NotBlank @Size(max = 32) String phone) {}

    public record Approval(@NotBlank @Size(max = 32) String whatsappSenderPhone) {}

    public record Activation(
            @NotBlank @Size(max = 32) String whatsappSenderPhone,
            @NotBlank @Size(max = 150) @Email @ValidDomain @NotDisposableEmail String email) {}

    public record RequestReceipt(UUID reference, String whatsappUrl) {}

    public record Review(long accountId, String accountEmail, boolean approved) {}

    public record Result(long playerId) {}
}
