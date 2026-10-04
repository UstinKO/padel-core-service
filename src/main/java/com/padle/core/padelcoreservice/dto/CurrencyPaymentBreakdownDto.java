package com.padle.core.padelcoreservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Детализация суммы оплаченных платежей одной валюты по способам оплаты
 * (LFPT-0475) — используется страницей платежей турнира (LFPT-0476) для
 * отображения под общей суммой по валюте.
 */
@Data
@AllArgsConstructor
public class CurrencyPaymentBreakdownDto {
    private String currency;
    private BigDecimal total;
    private List<PaymentMethodAmountDto> byMethod;
}
