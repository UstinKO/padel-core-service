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
public class MatchHistoryDTO {
    private Long id;
    private Integer round;
    private Integer courtNumber;
    private List<String> winners;
    private List<String> losers;
    private Integer winnersScore;
    private Integer losersScore;
    private String score;
    private String formattedResult;
}