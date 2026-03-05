package com.padle.core.padelcoreservice.model.enums;

public enum GenderFormat {
    MASCULINO("Masculino"),
    FEMENINO("Femenino"),
    MIXTO("Mixto");

    private final String value;

    GenderFormat(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static GenderFormat fromValue(String value) {
        for (GenderFormat format : GenderFormat.values()) {
            if (format.value.equals(value)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown gender format: " + value);
    }
}