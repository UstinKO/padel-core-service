package com.padle.core.padelcoreservice.dto.americano;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmericanoDropoutDto {
    @NotNull(message = "Player ID is required")
    private Long playerId;

    private String reason;
}