package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.CurrencyPaymentBreakdownDto;
import com.padle.core.padelcoreservice.dto.PaymentManagementViewDto;
import com.padle.core.padelcoreservice.dto.PaymentMethodAmountDto;
import com.padle.core.padelcoreservice.model.enums.PaymentMethod;
import com.padle.core.padelcoreservice.model.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-0475: getPaymentMethodBreakdown — чистая агрегация над уже загруженным списком
 * PaymentManagementViewDto, без обращений к репозиториям, поэтому Spring-контекст и БД
 * не нужны — конструируем PaymentService напрямую (остальные зависимости не используются
 * этим методом).
 */
class PaymentServiceBreakdownTest {

    private final PaymentService paymentService = new PaymentService(null, null, null, null);

    private static PaymentManagementViewDto row(String currency, PaymentStatus status,
                                                  PaymentMethod method, BigDecimal amount,
                                                  boolean partnerRow) {
        PaymentManagementViewDto dto = new PaymentManagementViewDto();
        dto.setCurrency(currency);
        dto.setPaymentStatus(status);
        dto.setPaymentMethod(method);
        dto.setAmount(amount);
        dto.setPartnerRow(partnerRow);
        return dto;
    }

    @Test
    void groupsPaidAmountsByCurrencyAndMethod() {
        List<PaymentManagementViewDto> players = List.of(
                row("ARS", PaymentStatus.PAID, PaymentMethod.BANK_TRANSFER, new BigDecimal("150000"), false),
                row("ARS", PaymentStatus.PAID, PaymentMethod.CASH, new BigDecimal("120000"), false),
                row("ARS", PaymentStatus.PAID, PaymentMethod.TARJETA, new BigDecimal("60000"), false),
                row("USDT", PaymentStatus.PAID, PaymentMethod.BY_BIT, new BigDecimal("120"), false),
                row("USDT", PaymentStatus.PAID, PaymentMethod.BINANCE, new BigDecimal("60"), false)
        );

        List<CurrencyPaymentBreakdownDto> breakdown = paymentService.getPaymentMethodBreakdown(players);

        assertThat(breakdown).hasSize(2);

        CurrencyPaymentBreakdownDto ars = breakdown.stream().filter(b -> b.getCurrency().equals("ARS")).findFirst().orElseThrow();
        assertThat(ars.getTotal()).isEqualByComparingTo("330000");
        assertThat(ars.getByMethod()).hasSize(3);
        assertThat(amountFor(ars, PaymentMethod.BANK_TRANSFER)).isEqualByComparingTo("150000");
        assertThat(amountFor(ars, PaymentMethod.CASH)).isEqualByComparingTo("120000");
        assertThat(amountFor(ars, PaymentMethod.TARJETA)).isEqualByComparingTo("60000");

        CurrencyPaymentBreakdownDto usdt = breakdown.stream().filter(b -> b.getCurrency().equals("USDT")).findFirst().orElseThrow();
        assertThat(usdt.getTotal()).isEqualByComparingTo("180");
        assertThat(amountFor(usdt, PaymentMethod.BY_BIT)).isEqualByComparingTo("120");
        assertThat(amountFor(usdt, PaymentMethod.BINANCE)).isEqualByComparingTo("60");
    }

    @Test
    void excludesNonPaidAndZeroOrNullAmounts() {
        List<PaymentManagementViewDto> players = List.of(
                row("ARS", PaymentStatus.PENDING, PaymentMethod.CASH, new BigDecimal("30000"), false),
                row("ARS", PaymentStatus.PAID, PaymentMethod.CASH, BigDecimal.ZERO, false),
                row("ARS", PaymentStatus.PAID, PaymentMethod.CASH, null, false),
                row(null, PaymentStatus.PAID, PaymentMethod.CASH, new BigDecimal("1000"), false)
        );

        List<CurrencyPaymentBreakdownDto> breakdown = paymentService.getPaymentMethodBreakdown(players);

        // Только последняя строка (currency == null трактуется как ARS) должна попасть в результат.
        assertThat(breakdown).hasSize(1);
        assertThat(breakdown.get(0).getCurrency()).isEqualTo("ARS");
        assertThat(breakdown.get(0).getTotal()).isEqualByComparingTo("1000");
    }

    @Test
    void nullPaymentMethodFormsSeparateGroupInsteadOfBeingDropped() {
        List<PaymentManagementViewDto> players = List.of(
                row("ARS", PaymentStatus.PAID, null, new BigDecimal("5000"), false),
                row("ARS", PaymentStatus.PAID, PaymentMethod.CASH, new BigDecimal("1000"), false)
        );

        List<CurrencyPaymentBreakdownDto> breakdown = paymentService.getPaymentMethodBreakdown(players);

        CurrencyPaymentBreakdownDto ars = breakdown.get(0);
        assertThat(ars.getTotal()).isEqualByComparingTo("6000");
        assertThat(ars.getByMethod()).hasSize(2);
        assertThat(ars.getByMethod().stream().anyMatch(m -> m.getMethod() == null
                && m.getAmount().compareTo(new BigDecimal("5000")) == 0)).isTrue();
    }

    @Test
    void partnerRowWithOwnPaymentIsIncludedNotDoubleCountedOrExcluded() {
        // Партнёр-гость ссылается на отдельную запись Payment (маркер PARTNER_PAYMENT в notes),
        // а не на тот же платёж основного игрока — оба независимы и оба должны учитываться.
        List<PaymentManagementViewDto> players = List.of(
                row("ARS", PaymentStatus.PAID, PaymentMethod.CASH, new BigDecimal("30000"), false),
                row("ARS", PaymentStatus.PAID, PaymentMethod.BANK_TRANSFER, new BigDecimal("30000"), true)
        );

        List<CurrencyPaymentBreakdownDto> breakdown = paymentService.getPaymentMethodBreakdown(players);

        CurrencyPaymentBreakdownDto ars = breakdown.get(0);
        assertThat(ars.getTotal()).isEqualByComparingTo("60000");
        assertThat(amountFor(ars, PaymentMethod.CASH)).isEqualByComparingTo("30000");
        assertThat(amountFor(ars, PaymentMethod.BANK_TRANSFER)).isEqualByComparingTo("30000");
    }

    @Test
    void returnsEmptyListForNoPayments() {
        assertThat(paymentService.getPaymentMethodBreakdown(List.of())).isEmpty();
    }

    private static BigDecimal amountFor(CurrencyPaymentBreakdownDto breakdown, PaymentMethod method) {
        return breakdown.getByMethod().stream()
                .filter(m -> m.getMethod() == method)
                .map(PaymentMethodAmountDto::getAmount)
                .findFirst()
                .orElseThrow();
    }
}
