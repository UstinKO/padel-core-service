package com.padle.core.padelcoreservice.service.americano;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ТЗ §4: таблица "сколько команд проходит в 1/4 финала напрямую, а сколько — через play-in (1/8)".
 * Значения взяты дословно из ТЗ (8→8/0 ... 16→0/16).
 */
class PlayoffFormatTest {

    @ParameterizedTest(name = "{0} equipos -> {1} directo, {2} play-in")
    @CsvSource({
            "8,  8, 0",
            "9,  7, 2",
            "10, 6, 4",
            "11, 5, 6",
            "12, 4, 8",
            "13, 3, 10",
            "14, 2, 12",
            "15, 1, 14",
            "16, 0, 16",
    })
    void matchesSpecTable(int teamCount, int expectedDirect, int expectedPlayIn) {
        assertThat(PlayoffFormat.directToQuarterFinal(teamCount)).isEqualTo(expectedDirect);
        assertThat(PlayoffFormat.playInTeams(teamCount)).isEqualTo(expectedPlayIn);
    }

    @Test
    void quarterFinalAlwaysHasExactlyEightSlots() {
        for (int n = 8; n <= 16; n++) {
            int direct = PlayoffFormat.directToQuarterFinal(n);
            int playInMatches = PlayoffFormat.playInTeams(n) / 2;
            assertThat(direct + playInMatches).isEqualTo(8);
        }
    }

    @Test
    void rejectsTeamCountsOutsideSupportedRange() {
        assertThatThrownBy(() -> PlayoffFormat.directToQuarterFinal(7))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlayoffFormat.directToQuarterFinal(17))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
