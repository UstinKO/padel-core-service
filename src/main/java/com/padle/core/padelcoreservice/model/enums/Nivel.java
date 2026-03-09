package com.padle.core.padelcoreservice.model.enums;

import lombok.Getter;

@Getter
public enum Nivel {
    C9("9"),
    C8("8"),
    C7("7"),
    C6("6"),
    C5("5"),
    PRINCIPIANTES("Principiante");

    private final String orden;

    Nivel(String orden) {
        this.orden = orden;
    }

    public String getDisplay() {
        return name();
    }
}
