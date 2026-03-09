package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.model.enums.PaymentMethod;
import com.padle.core.padelcoreservice.model.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentDto {
    private Long id;
    private Long registrationId;
    private String playerName;
    private String tournamentName;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private PaymentMethod paymentMethod;
    private LocalDateTime paymentDate;
    private String transactionId;
    private String paymentProvider;
    private String notes;
    private LocalDateTime createdAt;
}