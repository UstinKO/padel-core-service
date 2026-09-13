package com.padle.core.padelcoreservice.model.enums;

public enum AmericanoRoundStatus {
    PENDING("Pendiente"),
    /** LFPT-367: equipos ya definidos, esperando que se libere una cancha (solo para {@code AmericanoMatch}, no para rondas). */
    QUEUED("En cola"),
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