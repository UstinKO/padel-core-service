package com.padle.core.padelcoreservice.dto.americano;

import lombok.Data;

@Data
public class TeamPlayoffTeamRequest {
    private Long player1Id;
    private Long player2Id;
    private String player2Name;
    private String player2Phone;
    private String registrationSource;
    private Boolean hasPaid;
    private Boolean attended;
    private String adminComment;
}
