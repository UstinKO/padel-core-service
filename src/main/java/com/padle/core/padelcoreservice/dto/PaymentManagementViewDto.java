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
    private String playerPhone;
    private String telegramUsername;
    private Integer position;
    private Boolean attended;
    private Boolean participationConfirmed;

    // ── Поля для партнёра незарегистрированного на сайте ──
    private boolean isPartnerRow;           // ← ДОБАВЛЕНО: true = виртуальная строка партнёра
    private Long mainRegistrationId;        // ← ДОБАВЛЕНО: id основной регистрации пары
    private String partnerPhone;            // ← ДОБАВЛЕНО: телефон партнёра
    private String partnerEmail;            // ← ДОБАВЛЕНО: email партнёра

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

    // Служебное поле для группировки пар при нормализации позиций (не выводится в шаблоне)
    private Long pairGroupId;
}