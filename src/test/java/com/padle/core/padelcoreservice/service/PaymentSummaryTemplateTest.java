package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PaymentManagementViewDto;
import com.padle.core.padelcoreservice.model.enums.PaymentStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentSummaryTemplateTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"ARS", "USD", "EUR", "USDT"})
    void includesOnlyPaidAmountsAndKeepsIndependentPartnerPayment(String currency) {
        var paid = payment(currency, PaymentStatus.PAID, "10000");
        var partner = payment(currency, PaymentStatus.PAID, "2500");
        partner.setPartnerRow(true);
        var pending = payment(currency, PaymentStatus.PENDING, "7000");
        var refunded = payment(currency, PaymentStatus.REFUNDED, "9000");
        var unspecified = payment(currency, null, "3000");
        var failed = payment(currency, PaymentStatus.FAILED, "4000");
        var cancelled = payment(currency, PaymentStatus.CANCELLED, "5000");
        var processing = payment(currency, PaymentStatus.PROCESSING, "6000");

        String html = render(List.of(paid, partner, pending, refunded, unspecified, failed, cancelled, processing));

        assertThat(html).contains("12500");
        assertThat(html).doesNotContain("31500");
        assertThat(html.split("stat-item stat-money", -1)).hasSize(2);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"ARS", "USD", "EUR", "USDT"})
    void hidesCurrencyWithoutPositivePaidAmount(String currency) {
        String html = render(List.of(
                payment(currency, PaymentStatus.PENDING, "7000"),
                payment(currency, PaymentStatus.REFUNDED, "9000"),
                payment(currency, null, "3000"),
                payment(currency, PaymentStatus.PAID, "0")));

        assertThat(html).doesNotContain("stat-item stat-money");
    }

    private String render(List<PaymentManagementViewDto> players) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var context = new Context(Locale.forLanguageTag("es"));
        context.setVariable("players", players);
        return engine.process("admin/tournaments/payments", Set.of(".summary-stats"), context);
    }

    private PaymentManagementViewDto payment(String currency, PaymentStatus status, String amount) {
        var payment = new PaymentManagementViewDto();
        payment.setCurrency(currency);
        payment.setPaymentStatus(status);
        payment.setAmount(new BigDecimal(amount));
        payment.setAttended(false);
        return payment;
    }
}
