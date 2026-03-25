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
public class AmericanoStartRoundDto {
    @NotNull(message = "Points per match is required")
    private Integer pointsPerMatch;

    private String note;
}