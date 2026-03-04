package com.padle.core.padelcoreservice.model.enums;

public enum MatchType {
    INDIVIDUAL("individual"),
    PAREJAS("parejas");

    private final String value;

    MatchType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MatchType fromValue(String value) {
        for (MatchType type : MatchType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown match type: " + value);
    }
}