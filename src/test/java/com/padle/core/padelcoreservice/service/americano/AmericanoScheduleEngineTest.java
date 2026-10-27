package com.padle.core.padelcoreservice.service.americano;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Турнир №127 (20 участников/5 кортов/19 раундов) и остальные конфигурации полного цикла
 * (без bye) из ТЗ LFPT-0749 — обязательные условия (уникальность партнёров, корректность
 * раунда) проверяются как строгий инвариант на 100 независимых генерациях каждой
 * конфигурации; оптимизационные критерии (соперники/корты) — числовыми порогами,
 * откалиброванными по фактическому поведению движка (см. "Критерии приёмки" фиче-спеки).
 */
class AmericanoScheduleEngineTest {

    private static final int GENERATIONS_PER_CONFIG = 100;

    @ParameterizedTest(name = "{0} игроков, {1} кортов, {2} раундов")
    @CsvSource({
            "8, 2, 7",
            "12, 3, 11",
            "16, 4, 15",
            "20, 5, 19",
            "24, 6, 23"
    })
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void fullCycleConfigurations_neverViolateMandatoryConstraints(int players, int courts, int rounds) {
        assertThat(AmericanoScheduleEngine.isPerfectCycleAchievable(players, courts, rounds)).isTrue();

        for (int attempt = 0; attempt < GENERATIONS_PER_CONFIG; attempt++) {
            List<List<AmericanoScheduleEngine.MatchSlot>> schedule =
                    AmericanoScheduleEngine.generate(players, courts, rounds);
            AmericanoScheduleValidator.ScheduleReport report =
                    AmericanoScheduleValidator.validate(players, courts, schedule);

            assertThat(report.hardViolations()).as("попытка %d, нарушения раунда", attempt).isEmpty();
            assertThat(report.partnerRepeats()).as("попытка %d, повторы партнёров", attempt).isZero();
            assertThat(report.playersWithoutPartner()).as("попытка %d, пары без партнёрства", attempt).isZero();
        }
    }

    @ParameterizedTest(name = "{0} игроков, {1} кортов, {2} раундов")
    @CsvSource({
            "20, 5, 19, 3, 3",
            "24, 6, 23, 3, 3"
    })
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void reportedConfigurations_keepOpponentsAndCourtsWellBalanced(
            int players, int courts, int rounds, int maxOpponentSpread, int maxConsecutiveSameCourt) {
        for (int attempt = 0; attempt < GENERATIONS_PER_CONFIG; attempt++) {
            List<List<AmericanoScheduleEngine.MatchSlot>> schedule =
                    AmericanoScheduleEngine.generate(players, courts, rounds);
            AmericanoScheduleValidator.ScheduleReport report =
                    AmericanoScheduleValidator.validate(players, courts, schedule);

            int opponentSpread = report.maxOpponentMeetings() - report.minOpponentMeetings();
            assertThat(opponentSpread).as("попытка %d, разброс встреч соперников", attempt)
                    .isLessThanOrEqualTo(maxOpponentSpread);
            assertThat(report.maxConsecutiveSameCourt()).as("попытка %d, серия на одном корте", attempt)
                    .isLessThanOrEqualTo(maxConsecutiveSameCourt);
        }
    }

    @ParameterizedTest(name = "{0} игроков, {1} кортов, {2} раундов")
    @CsvSource({
            "8, 2, 7, 5, 4",
            "12, 3, 11, 3, 4",
            "16, 4, 15, 3, 4"
    })
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void smallerCourtConfigurations_stayWithinLooserBounds(
            int players, int courts, int rounds, int maxOpponentSpread, int maxConsecutiveSameCourt) {
        for (int attempt = 0; attempt < GENERATIONS_PER_CONFIG; attempt++) {
            List<List<AmericanoScheduleEngine.MatchSlot>> schedule =
                    AmericanoScheduleEngine.generate(players, courts, rounds);
            AmericanoScheduleValidator.ScheduleReport report =
                    AmericanoScheduleValidator.validate(players, courts, schedule);

            int opponentSpread = report.maxOpponentMeetings() - report.minOpponentMeetings();
            assertThat(opponentSpread).as("попытка %d, разброс встреч соперников", attempt)
                    .isLessThanOrEqualTo(maxOpponentSpread);
            assertThat(report.maxConsecutiveSameCourt()).as("попытка %d, серия на одном корте", attempt)
                    .isLessThanOrEqualTo(maxConsecutiveSameCourt);
        }
    }

    @Test
    void isPerfectCycleAchievable_falseWhenByeUnavoidableOrCycleExceeded() {
        assertThat(AmericanoScheduleEngine.isPerfectCycleAchievable(20, 5, 19)).isTrue();
        assertThat(AmericanoScheduleEngine.isPerfectCycleAchievable(21, 5, 19)).isFalse(); // N не кратно 4
        assertThat(AmericanoScheduleEngine.isPerfectCycleAchievable(24, 5, 19)).isFalse(); // N > courts*4
        assertThat(AmericanoScheduleEngine.isPerfectCycleAchievable(20, 5, 20)).isFalse(); // раундов больше цикла
    }

    @Test
    void byeConfiguration_producesValidRoundsWithoutHangingOrCrashing() {
        // 21 игрок, 5 кортов (playersPerRound=20) — bye неизбежен, нет гарантии 0 повторов,
        // но раунды должны оставаться структурно корректными.
        List<List<AmericanoScheduleEngine.MatchSlot>> schedule = AmericanoScheduleEngine.generate(21, 5, 10);
        AmericanoScheduleValidator.ScheduleReport report = AmericanoScheduleValidator.validate(21, 5, schedule);

        assertThat(schedule).hasSize(10);
        assertThat(report.hardViolations()).isEmpty();
    }
}
