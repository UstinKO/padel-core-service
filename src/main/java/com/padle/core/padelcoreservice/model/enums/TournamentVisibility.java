package com.padle.core.padelcoreservice.model.enums;

public enum TournamentVisibility {
    PUBLICO("Público"),
    SOLO_POR_ENLACE("Solo por enlace");

    private final String value;

    TournamentVisibility(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
