package com.padle.core.padelcoreservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Objects;

/** Расчёт денежных условий отмены парного Americano без чтения или изменения БД. */
public final class PairCancellationPolicy {

    static final BigDecimal LATE_FRACTION = new BigDecimal("0.50");

    private PairCancellationPolicy() {
    }

    public record Quote(LocalDateTime freeUntil, boolean late, BigDecimal pairPrice,
                        BigDecimal obligation, BigDecimal alreadyPaid,
                        BigDecimal remainingDue, BigDecimal refundable) {
    }

    /**
     * Граница бесплатной отмены включительная. Оплаченная замена обнуляет обязательство;
     * возврат фиксируется отдельно организатором, этот расчёт денег не переводит.
     */
    public static Quote quote(LocalDateTime startsAt, LocalDateTime cancellationAt,
                              BigDecimal pricePerPlayer, BigDecimal alreadyPaid,
                              boolean replacementPaid) {
        return quoteFromTerms(startsAt.minusHours(3), cancellationAt, pricePerPlayer, LATE_FRACTION,
                alreadyPaid, replacementPaid);
    }

    static Quote quoteFromTerms(LocalDateTime freeUntil, LocalDateTime cancellationAt,
                                BigDecimal pricePerPlayer, BigDecimal lateFraction, BigDecimal alreadyPaid,
                                boolean replacementPaid) {
        Objects.requireNonNull(cancellationAt, "cancellationAt");
        Objects.requireNonNull(pricePerPlayer, "pricePerPlayer");
        Objects.requireNonNull(alreadyPaid, "alreadyPaid");
        if (pricePerPlayer.signum() < 0 || alreadyPaid.signum() < 0) {
            throw new IllegalArgumentException("Цена и внесённая оплата не могут быть отрицательными");
        }

        boolean late = cancellationAt.isAfter(freeUntil);
        BigDecimal pairPrice = pricePerPlayer.multiply(BigDecimal.valueOf(2)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = alreadyPaid.setScale(2, RoundingMode.HALF_UP);
        BigDecimal obligation = late && !replacementPaid
                ? pairPrice.multiply(lateFraction).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        return new Quote(freeUntil, late, pairPrice, obligation, paid,
                obligation.subtract(paid).max(BigDecimal.ZERO).setScale(2),
                paid.subtract(obligation).max(BigDecimal.ZERO).setScale(2));
    }
}
