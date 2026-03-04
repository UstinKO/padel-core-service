package com.padle.core.padelcoreservice.model.enums;

public enum TournamentType {
    KING_OF_COURT("Король Корта"),
    AMERICANA("Американка");

    private final String value;

    TournamentType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TournamentType fromValue(String value) {
        for (TournamentType type : TournamentType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown tournament type: " + value);
    }
}