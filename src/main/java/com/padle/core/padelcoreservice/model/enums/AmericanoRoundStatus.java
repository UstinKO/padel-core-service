package com.padle.core.padelcoreservice.model.enums;

public enum AmericanoRoundStatus {
    PENDING("Pendiente"),
    IN_PROGRESS("En curso"),
    COMPLETED("Completado"),
    CANCELLED("Cancelado");

    private final String value;

    AmericanoRoundStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}