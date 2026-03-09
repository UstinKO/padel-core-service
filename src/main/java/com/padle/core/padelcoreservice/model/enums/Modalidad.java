package com.padle.core.padelcoreservice.model.enums;

public enum Modalidad {
    DOBLES("Dobles", "Парный"),
    INDIVIDUAL("Individual", "Одиночный");

    private final String nombre; // Отображаемое имя для испанского (как в UI)
    private final String description; // Для внутреннего использования или i18n

    Modalidad(String nombre, String description) {
        this.nombre = nombre;
        this.description = description;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescription() {
        return description;
    }
}
