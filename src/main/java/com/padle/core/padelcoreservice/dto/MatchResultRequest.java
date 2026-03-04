package com.padle.core.padelcoreservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResultRequest {

    @NotNull
    private Long roundId;

    @NotNull
    private Integer courtNumber;

    @NotNull
    private List<Long> winners;

    @NotNull
    private List<Long> losers;

    @NotNull
    private Integer winnersScore;

    @NotNull
    private Integer losersScore;
}