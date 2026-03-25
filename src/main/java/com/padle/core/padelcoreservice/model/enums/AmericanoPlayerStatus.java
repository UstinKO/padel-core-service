package com.padle.core.padelcoreservice.model.enums;

public enum AmericanoPlayerStatus {
    ACTIVE("Activo"),
    DROPPED_OUT("Abandonó"),
    DISQUALIFIED("Descalificado");

    private final String value;

    AmericanoPlayerStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}