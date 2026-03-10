package com.padle.core.padelcoreservice.model.enums;

import lombok.Getter;

@Getter
public enum PaymentMethod {
    // Офлайн методы
    CASH("Efectivo", "Наличные"),
    BANK_TRANSFER("Transferencia Bancaria", "Банковский перевод"),
    BY_BIT("By Bit", "Бай бит"),
    BINANCE("Binance", "Бинанс"),
    TARJETA("Tarjeta", "Тарьета"),
    TRANSFERENCIA("Transferencia", "Перевод"),

    // Другие
    OTHER("Otro", "Другой");

    private final String displayName;
    private final String description;

    PaymentMethod(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}