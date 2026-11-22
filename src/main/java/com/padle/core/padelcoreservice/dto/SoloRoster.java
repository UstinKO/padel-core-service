package com.padle.core.padelcoreservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class SoloRoster {
    private SoloRoster() {}
    public enum Action { ADD, PAIR, REMOVE }
    public enum Kind { SEARCH, ADD_LATER }
    public record PlayerInput(@Positive Long id, @Size(max = 100) String firstName,
                              @Size(max = 100) String lastName, @Size(max = 30) String phone,
                              @Size(max = 33) String telegram) {}
    public record Command(@NotNull UUID requestId, @NotNull Action action, @Positive Long registrationId,
                          @Positive Long partnerRegistrationId, @Valid PlayerInput player, Kind kind,
                          boolean overrideLevel, @Size(max = 64) String expectedState) {}
    public record Money(Long id, BigDecimal amount, String currency, String status, String method,
                        String transactionId, String notes) {}
    public record Participant(Long registrationId, Long playerId, String name, String level, String status,
                              boolean reserve, boolean attended, List<Money> payments) {}
    public record Preview(String state, List<Participant> participants, String resultingStatus,
                          boolean levelOverrideRequired, Long existingRegistrationId, String existingPair) {}
    public record Result(UUID requestId, Long registrationId, Long partnerRegistrationId,
                         String status, boolean existing) {}
    public record History(UUID requestId, String action, Long actorId, LocalDateTime at,
                          String before, String after) {}
    public record Board(List<Participant> singles, List<Participant> cancelled, List<History> history) {}
    public record Found(Long playerId, String name, String level, Long registrationId,
                        boolean single, String pair, String status) {}
}
