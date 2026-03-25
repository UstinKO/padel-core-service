package com.padle.core.padelcoreservice.dto.americano;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmericanoMatchResultDto {
    @NotNull(message = "Match ID is required")
    private Long matchId;

    @NotNull(message = "Team 1 score is required")
    @Min(value = 0, message = "Score must be non-negative")
    private Integer team1Score;

    @NotNull(message = "Team 2 score is required")
    @Min(value = 0, message = "Score must be non-negative")
    private Integer team2Score;
}