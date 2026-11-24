package com.padle.core.padelcoreservice.service.calendar;

import com.padle.core.padelcoreservice.model.enums.Nivel;
import java.util.Arrays;
import java.util.List;

public final class DeclaredEventLevel {
    private DeclaredEventLevel() {}

    public static boolean matches(String declared, Nivel selected) {
        return selected.name().equals(declared) || (declared != null && selected.name().matches("[CD][0-9]")
                && Arrays.asList(declared.split("_")).contains(selected.name()));
    }

    public static List<Nivel> including(Nivel selected) {
        return Arrays.stream(Nivel.values()).filter(level -> matches(level.name(), selected)).toList();
    }
}
