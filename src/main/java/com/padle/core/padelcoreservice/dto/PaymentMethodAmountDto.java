package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.model.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Сумма оплаченных платежей одной валюты по одному способу оплаты.
 * {@code method == null} — платёж подтверждён (PAID), но способ оплаты не выбран.
 */
@Data
@AllArgsConstructor
public class PaymentMethodAmountDto {
    private PaymentMethod method;
    private BigDecimal amount;
}
