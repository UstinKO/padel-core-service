package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.model.enums.PaymentMethod;
import com.padle.core.padelcoreservice.model.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Write-модель для PaymentManagementController.savePayments() (issue #130) —
 * только поля, реально нужные PaymentService.savePaymentManagementData().
 * Отдельно от PaymentManagementViewDto, который остаётся read-моделью для
 * страницы управления платежами (там полей вдвое больше, они не нужны на запись).
 */
@Data
public class PaymentUpdateDto {
    private Long registrationId;
    private Long paymentId;
    private boolean hasPayment;
    private boolean partnerRow;
    private boolean attended;
    private boolean participationConfirmed;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
    private String transactionId;
    private String notes;
}
