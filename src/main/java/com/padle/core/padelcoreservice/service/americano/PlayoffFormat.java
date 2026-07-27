package com.padle.core.padelcoreservice.service.americano;

/**
 * ТЗ §4 (Team Playoff, 9-16 команд, T13): таблица "сколько команд проходит в 1/4 финала
 * напрямую, а сколько — через play-in (1/8 финала)". Четвертьфинал всегда состоит ровно
 * из 8 слотов: {@code directToQuarterFinal(n) + playInTeams(n) / 2 == 8} для любого n в [8,16].
 */
final class PlayoffFormat {

    private PlayoffFormat() {
    }

    /** Нижняя граница диапазона, где действует таблица §4 (8 команд — все проходят напрямую, play-in не нужен). */
    static final int MIN_TEAMS_WITH_TABLE = 8;

    /** Верхняя граница: при 16 командах все играют 1/8 финала, свободных слотов не остаётся. */
    static final int MAX_TEAMS_WITH_TABLE = 16;

    /** Команд, проходящих в четвертьфинал напрямую без игры в 1/8 финала. */
    static int directToQuarterFinal(int teamCount) {
        if (teamCount < MIN_TEAMS_WITH_TABLE || teamCount > MAX_TEAMS_WITH_TABLE) {
            throw new IllegalArgumentException(
                    "Tabla §4 solo definida para 8-16 equipos, recibido: " + teamCount);
        }
        return MAX_TEAMS_WITH_TABLE - teamCount;
    }

    /** Команд, играющих 1/8 финала (play-in) — чётное число, каждая пара даёт один свободный слот 1/4. */
    static int playInTeams(int teamCount) {
        return teamCount - directToQuarterFinal(teamCount);
    }
}
