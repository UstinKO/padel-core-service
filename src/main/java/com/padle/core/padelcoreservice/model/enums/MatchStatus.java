package com.padle.core.padelcoreservice.model.enums;

public enum MatchStatus {
    PROGRAMADO("programado"),
    EN_CURSO("en_curso"),
    FINALIZADO("finalizado"),
    SUSPENDIDO("suspendido"),
    CANCELADO("cancelado"),
    WALKOVER("walkover"); // Cuando un jugador no se presenta

    private final String value;

    MatchStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MatchStatus fromValue(String value) {
        for (MatchStatus status : MatchStatus.values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown match status: " + value);
    }
}