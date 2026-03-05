package com.padle.core.padelcoreservice.model.enums;

public enum TournamentStatus {
    BORRADOR("Borrador"),
    PUBLICADO("Publicado"),
    REGISTRO_ABIERTO("Registro Abierto"),
    CERRADO("Cerrado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String value;

    TournamentStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}