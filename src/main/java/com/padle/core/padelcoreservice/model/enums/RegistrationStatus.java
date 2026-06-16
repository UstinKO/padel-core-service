package com.padle.core.padelcoreservice.model.enums;

public enum RegistrationStatus {
    CONFIRMED("Confirmado"),
    WAITLIST("Lista de Espera"),
    WAITLIST_INVITED("Invitado a Confirmar"),
    CANCELLED("Cancelado"),
    PARTICIPATED("Participó"),

    // Статусы для парных турниров
    PENDING_PARTNER("Esperando Compañero"),      // Ожидает подтверждения партнера
    PARTNER_INVITED("Compañero Invitado"),       // Партнер приглашен (незарегистрированный)
    PAIR_REGISTERED("Pareja Registrada"),        // Оба игрока зарегистрированы

    // Соло-регистрация на парный турнир (без пары)
    SOLO_ADD_LATER("Agrega compañero luego"),    // Добавит данные партнёра позже
    SOLO_SEARCH("Buscando compañero"),           // Ищет партнёра через сайт
    SOLO_EXPIRED("Plazo vencido");               // Истёк срок добавления партнёра

    private final String value;

    RegistrationStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}