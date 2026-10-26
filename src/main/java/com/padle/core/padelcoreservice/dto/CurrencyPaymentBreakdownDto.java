package com.padle.core.padelcoreservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Детализация суммы оплаченных платежей одной валюты по способам оплаты —
 * показывается на странице платежей турнира под общей суммой по валюте.
 */
@Data
@AllArgsConstructor
public class CurrencyPaymentBreakdownDto {
    private String currency;
    private BigDecimal total;
    private List<PaymentMethodAmountDto> byMethod;
}
