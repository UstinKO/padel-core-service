package com.padle.core.padelcoreservice.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairCancellationPolicyTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 7, 15, 0);

    @Test
    void exactlyThreeHoursBeforeStartIsFree() {
        var quote = PairCancellationPolicy.quote(START, START.minusHours(3),
                new BigDecimal("10000"), BigDecimal.ZERO, false);

        assertFalse(quote.late());
        assertEquals(new BigDecimal("0.00"), quote.obligation());
        assertEquals(START.minusHours(3), quote.freeUntil());
    }

    @Test
    void oneSecondAfterBoundaryChargesHalfOfPairPriceLessPayments() {
        var quote = PairCancellationPolicy.quote(START, START.minusHours(3).plusSeconds(1),
                new BigDecimal("10000"), new BigDecimal("4000"), false);

        assertTrue(quote.late());
        assertEquals(new BigDecimal("20000.00"), quote.pairPrice());
        assertEquals(new BigDecimal("10000.00"), quote.obligation());
        assertEquals(new BigDecimal("6000.00"), quote.remainingDue());
        assertEquals(new BigDecimal("0.00"), quote.refundable());
    }

    @Test
    void overpaymentAndPaidReplacementBecomeManualRefunds() {
        var late = START.minusMinutes(20);
        var overpaid = PairCancellationPolicy.quote(START, late,
                new BigDecimal("10000"), new BigDecimal("12000"), false);
        var replaced = PairCancellationPolicy.quote(START, late,
                new BigDecimal("10000"), new BigDecimal("12000"), true);

        assertEquals(new BigDecimal("2000.00"), overpaid.refundable());
        assertEquals(new BigDecimal("0.00"), overpaid.remainingDue());
        assertEquals(new BigDecimal("0.00"), replaced.obligation());
        assertEquals(new BigDecimal("12000.00"), replaced.refundable());
    }
}
