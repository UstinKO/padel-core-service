package com.padle.core.padelcoreservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LookingForPartnerDto {
    private Long registrationId;
    private Long playerId;
    private String playerName;
    private String nivel;
    private Boolean shareContacts;
    private String telegramUsername;
    private String telefono;
}
