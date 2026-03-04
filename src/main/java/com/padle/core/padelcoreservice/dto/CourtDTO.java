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
public class CourtDTO {
    private Long id;
    private Integer courtNumber;
    private List<PlayerInfoDTO> players;
    private List<TeamDTO> teams;
    private CourtResultDTO result;
    private Boolean hasResult;
}