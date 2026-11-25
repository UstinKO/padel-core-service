package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.model.enums.ParticipationStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public final class ParticipationDtos {
    private ParticipationDtos() {}

    public enum Decision { CONFIRM, DECLINE }

    public record Answer(@NotNull Decision decision) {}
    public record ReplacementSearch(@NotNull Boolean enabled) {}

    public record RequestSummary(UUID requestId, Long registrationId, Long playerId, String playerName,
                                 ParticipationStatus status, boolean delegated) {}

    public record Review(UUID requestId, Long tournamentId, String tournamentName, String playerName,
                         ParticipationStatus status, boolean delegated, boolean canRespond, boolean canDecline,
                         boolean expired, boolean outdated) {}

    public record OrganizerStatus(Long registrationId, Long playerId, String playerName,
                                  ParticipationStatus participationStatus, boolean replacementNeeded) {}
}
