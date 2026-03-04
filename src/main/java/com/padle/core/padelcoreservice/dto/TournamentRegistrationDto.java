package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TournamentRegistrationDto {
    private Long id;
    private Long tournamentId;
    private String tournamentNombre;
    private Long playerId;
    private String playerNombre;
    private String playerApellido;
    private String playerEmail;
    private LocalDateTime registrationDate;
    private RegistrationStatus status;
    private Integer position;
    private Integer waitlistPosition;
    private LocalDateTime cancellationDate;
    private String cancellationReason;
    private Boolean notifiedAboutVacancy;
}