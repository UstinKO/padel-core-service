package com.padle.core.padelcoreservice.model.enums;

import lombok.Getter;

@Getter
public enum PaymentMethod {
    // Офлайн методы
    CASH("Efectivo", "Наличные"),
    BANK_TRANSFER("Transferencia Bancaria", "Банковский перевод"),

    // Онлайн методы (для будущей интеграции)
    MERCADO_PAGO("Mercado Pago", "Mercado Pago"),
    STRIPE("Stripe", "Stripe"),
    PAYPAL("PayPal", "PayPal"),

    // Другие
    OTHER("Otro", "Другой");

    private final String displayName;
    private final String description;

    PaymentMethod(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}