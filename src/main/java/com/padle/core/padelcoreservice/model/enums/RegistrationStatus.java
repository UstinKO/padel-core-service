package com.padle.core.padelcoreservice.model.enums;

public enum RegistrationStatus {
    CONFIRMED("Confirmado"),
    WAITLIST("Lista de Espera"),
    WAITLIST_INVITED("Invitado a Confirmar"),
    CANCELLED("Cancelado"),
    PARTICIPATED("Participó");

    private final String value;

    RegistrationStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}