package com.padle.core.padelcoreservice.dto.americano;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record RoundRobinAdditionalMatchRequest(
        @NotNull Long team1Id,
        @NotNull Long team2Id,
        @NotNull @Min(1) Integer courtNumber,
        @Min(1) Integer placementStart,
        @Size(max = 80) String label,
        @NotNull UUID requestId) {}
