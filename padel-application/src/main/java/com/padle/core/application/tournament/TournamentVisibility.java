package com.padle.core.application.tournament;

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
