package com.padle.core.padelcoreservice.model.enums;

public enum RegistrationStatus {
    CONFIRMED("Confirmado"),
    WAITLIST("Lista de Espera"),
    WAITLIST_INVITED("Invitado a Confirmar"),
    CANCELLED("Cancelado"),
    PARTICIPATED("Participó"),

    // Новые статусы для парных турниров
    PENDING_PARTNER("Esperando Compañero"),      // Ожидает подтверждения партнера
    PARTNER_INVITED("Compañero Invitado"),       // Партнер приглашен (незарегистрированный)
    PAIR_REGISTERED("Pareja Registrada");        // Оба игрока зарегистрированы

    private final String value;

    RegistrationStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}