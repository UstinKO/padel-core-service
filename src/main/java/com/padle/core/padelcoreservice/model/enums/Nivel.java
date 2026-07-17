package com.padle.core.padelcoreservice.model.enums;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

@Getter
public enum Nivel {
    // Существующие уровни
    C9("9"),
    C8("8"),
    C7("7"),
    C6("6"),
    C5("5"),
    PRINCIPIANTES("Principiante"),

    // Новые уровни по просьбе заказчика
    SUMA_15("15+"),
    SUMA_13("13+"),
    D7("D7"),
    D8("D8"),
    D7_D8("D7/D8"),
    D6("D6"),
    C7_C6("C7/C6"),
    C4("C4"),
    SUMA_14("14+"),

    // Категория "Todos" по просьбе заказчика
    TODOS("Todos");

    private final String orden;

    Nivel(String orden) {
        this.orden = orden;
    }

    public String getDisplay() {
        return name();
    }

    // Уровни, реально применимые к игроку (players/perfil.html, admin/players/details.html).
    // Остальные значения Nivel — категории турниров (составные/комбинированные), их
    // нельзя присваивать игроку напрямую — согласовано с заказчиком (см. issue #206).
    private static final Set<Nivel> PLAYER_LEVELS = EnumSet.of(C9, C8, C7, C6, C5, D7, D8, D6, C4);

    public static boolean isPlayerLevel(Nivel nivel) {
        return nivel != null && PLAYER_LEVELS.contains(nivel);
    }
}