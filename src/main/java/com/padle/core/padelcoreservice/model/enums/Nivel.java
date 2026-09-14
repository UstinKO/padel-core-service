package com.padle.core.padelcoreservice.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public enum Nivel {
    // Masculino — individual
    C9("9", GenderFormat.MASCULINO),
    C8("8", GenderFormat.MASCULINO),
    C7("7", GenderFormat.MASCULINO),
    C6("6", GenderFormat.MASCULINO),
    C5("5", GenderFormat.MASCULINO),
    C4("4", GenderFormat.MASCULINO),

    // Masculino — categorías adyacentes (LFPT-373)
    C9_C8("C9/C8", GenderFormat.MASCULINO),
    C8_C7("C8/C7", GenderFormat.MASCULINO),
    C7_C6("C7/C6", GenderFormat.MASCULINO),
    C6_C5("C6/C5", GenderFormat.MASCULINO),
    C5_C4("C5/C4", GenderFormat.MASCULINO),

    // Femenino — individual
    D9("D9", GenderFormat.FEMENINO),
    D8("D8", GenderFormat.FEMENINO),
    D7("D7", GenderFormat.FEMENINO),
    D6("D6", GenderFormat.FEMENINO),
    D5("D5", GenderFormat.FEMENINO),
    D4("D4", GenderFormat.FEMENINO),

    // Femenino — categorías adyacentes. D7_D8 es el valor histórico para la pareja D7/D8
    // (ya usado por torneos existentes) — no se crea un D8_D7 duplicado, se reutiliza.
    D9_D8("D9/D8", GenderFormat.FEMENINO),
    D7_D8("D7/D8", GenderFormat.FEMENINO),
    D7_D6("D7/D6", GenderFormat.FEMENINO),
    D6_D5("D6/D5", GenderFormat.FEMENINO),
    D5_D4("D5/D4", GenderFormat.FEMENINO),

    // Mixto — suma de niveles
    SUMA_16("16+", GenderFormat.MIXTO),
    SUMA_15("15+", GenderFormat.MIXTO),
    SUMA_14("14+", GenderFormat.MIXTO),
    SUMA_13("13+", GenderFormat.MIXTO),
    SUMA_12("12+", GenderFormat.MIXTO),
    SUMA_11("11+", GenderFormat.MIXTO),
    SUMA_10("10+", GenderFormat.MIXTO),

    // Категория "Todos" по просьбе заказчика — больше не предлагается при создании
    // нового турнира (LFPT-373), не привязана ни к одному generoFormato.
    TODOS("Todos", null),

    // Устаревшее значение — больше не предлагается при создании нового турнира (LFPT-373),
    // не привязано ни к одному generoFormato. Существующие турниры с этим значением
    // продолжают корректно отображаться (без принудительной миграции).
    PRINCIPIANTES("Principiante", null),

    // issue #82: nivelJugador стал обязательным полем — этот sentinel НЕ предлагается
    // игроку как выбор (исключён из /registro и /perfil, там реального выбора уровня
    // достаточно), используется только для (а) миграции v1.44, забэкфилившей игроков,
    // у которых nivel_jugador был NULL до этой задачи, и (б) DEFAULT колонки на будущее —
    // OAuth2/гостевая авто-регистрация игрока не собирают уровень при создании аккаунта.
    // Админ по-прежнему может явно проставить его игроку через /admin/players (сброс
    // ошибочно указанного уровня) — оттуда и только оттуда этот sentinel может появиться
    // у игрока уже после регистрации. Никогда не был допустимым значением для турнира
    // (не входил в CHECK-constraint categoria_nivel ни в одной из прошлых миграций).
    SIN_ESPECIFICAR("Sin especificar", null);

    private final String orden;
    private final GenderFormat generoAplicable;

    Nivel(String orden, GenderFormat generoAplicable) {
        this.orden = orden;
        this.generoAplicable = generoAplicable;
    }

    public String getDisplay() {
        return name();
    }

    // Для шаблона: атрибут пустой строкой, если значение не привязано ни к какому полу
    // (легаси/служебные значения) — используется JS-фильтрацией на форме турнира.
    public String getGeneroAplicableNombre() {
        return generoAplicable == null ? "" : generoAplicable.name();
    }

    // Значения, которые больше не предлагаются при создании нового турнира (LFPT-373) —
    // не привязаны ни к одному generoFormato, оставлены только для отображения уже
    // существующих турниров с этими значениями.
    private static final Set<Nivel> LEGACY_TOURNAMENT_LEVELS = EnumSet.of(PRINCIPIANTES, TODOS, SIN_ESPECIFICAR);

    public boolean isLegacyTournamentLevel() {
        return LEGACY_TOURNAMENT_LEVELS.contains(this);
    }

    // Список значений для селекта "Categoría / Nivel" формы турнира: все актуальные
    // (привязанные к generoFormato) значения + текущее значение турнира, даже если оно
    // устаревшее — иначе на форме редактирования уже сохранённое устаревшее значение
    // пропадёт из списка и будет молча заменено пустым при сохранении формы.
    // currentValue == null (создание нового турнира) — легаси-значения не включаются.
    public static List<Nivel> forTournamentForm(Nivel currentValue) {
        return Arrays.stream(values())
                .filter(nivel -> !nivel.isLegacyTournamentLevel() || nivel == currentValue)
                .collect(Collectors.toList());
    }

    // Parseo defensivo del valor de formulario (puede venir vacío o inválido en un
    // re-render tras error de validación) — evita duplicar try/catch en cada controlador.
    public static Nivel parseOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Уровни, реально применимые к игроку (players/perfil.html, admin/players/details.html).
    // Остальные значения Nivel — категории турниров (составные/комбинированные), их
    // нельзя присваивать игроку напрямую — согласовано с заказчиком (см. issue #206).
    private static final Set<Nivel> PLAYER_LEVELS = EnumSet.of(C9, C8, C7, C6, C5, D9, D7, D8, D6, C4);

    public static boolean isPlayerLevel(Nivel nivel) {
        return nivel != null && PLAYER_LEVELS.contains(nivel);
    }
}
