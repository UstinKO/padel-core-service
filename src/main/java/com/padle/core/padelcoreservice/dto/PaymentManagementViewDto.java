package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.model.enums.PaymentMethod;
import com.padle.core.padelcoreservice.model.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentManagementViewDto {
    // Данные регистрации
    private Long registrationId;
    private Long playerId;
    private String playerName;
    private String playerEmail;
    private Integer position;
    private Boolean attended;

    // Данные платежа
    private Long paymentId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private String transactionId;
    private String notes;

    // Флаг для нового платежа (если еще нет платежа)
    private boolean hasPayment;
}