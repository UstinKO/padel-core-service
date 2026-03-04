package com.padle.core.padelcoreservice.model.enums;

public enum Nivel {
    C9(9, "Principiante"),
    C8(8, "Intermedio"),
    C7(7, "Avanzado"),
    C6(6, "Profesional"),
    C5(5, "Élite");

    private final int orden;
    private final String descripcion;

    Nivel(int orden, String descripcion) {
        this.orden = orden;
        this.descripcion = descripcion;
    }

    public int getOrden() {
        return orden;
    }

    public String getDisplay() {
        return name() + " - " + descripcion;
    }
}
