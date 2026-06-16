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
    private String playerPhone;
    private String playerTelegram;
    private LocalDateTime registrationDate;
    private RegistrationStatus status;
    private Integer position;
    private Integer waitlistPosition;
    private LocalDateTime cancellationDate;
    private String cancellationReason;
    private Boolean notifiedAboutVacancy;

    // Соло-регистрация на парный турнир
    private Boolean shareContacts;

    // Новые поля для парного турнира
    private Boolean isDoubleRegistration;
    private Long mainPlayerId;
    private Long partnerId;
    private String partnerNombre;
    private String partnerApellido;
    private String partnerEmail;
    private String partnerPhone;
    private String partnerTelegram;
    private Boolean partnerRegistered;
    private String partnerRegistrationToken;
    private LocalDateTime partnerTokenExpiry;
}