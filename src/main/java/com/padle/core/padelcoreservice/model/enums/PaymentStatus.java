package com.padle.core.padelcoreservice.model.enums;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    PENDING("Pendiente", "Ожидает оплаты"),
    PROCESSING("Procesando", "В обработке"),
    PAID("Pagado", "Оплачено"),
    FAILED("Fallido", "Ошибка оплаты"),
    REFUNDED("Reintegrado", "Возврат"),
    CANCELLED("Cancelado", "Отменен");

    private final String displayName;
    private final String description;

    PaymentStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
}