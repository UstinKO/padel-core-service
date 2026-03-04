package com.padle.core.padelcoreservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourtResultDTO {
    private Long id;
    private List<Long> winners;
    private List<Long> losers;
    private Integer winnersScore;
    private Integer losersScore;
    private List<String> winnerNames;
    private List<String> loserNames;
}
