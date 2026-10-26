package com.padle.core.padelcoreservice.model.enums;

public enum AmericanoRoundStatus {
    PENDING("Pendiente"),
    /** Команды уже определены и ждут, пока освободится корт (только для {@code AmericanoMatch}, не для раундов). */
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