package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.service.americano.AmericanoScheduleEngine.MatchSlot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AmericanoScheduleValidatorTest {

    @Test
    void validSingleRound_hasNoHardViolations() {
        List<List<MatchSlot>> schedule = List.of(List.of(
                new MatchSlot(0, 1, 2, 3, 1),
                new MatchSlot(4, 5, 6, 7, 2)
        ));

        AmericanoScheduleValidator.ScheduleReport report = AmericanoScheduleValidator.validate(8, 2, schedule);

        assertThat(report.hardViolations()).isEmpty();
        assertThat(report.isValid()).isTrue();
    }

    @Test
    void playerTwiceInSameRound_isHardViolation() {
        List<List<MatchSlot>> schedule = List.of(List.of(
                new MatchSlot(0, 1, 2, 3, 1),
                new MatchSlot(0, 5, 6, 7, 2) // игрок 0 второй раз в этом же раунде
        ));

        AmericanoScheduleValidator.ScheduleReport report = AmericanoScheduleValidator.validate(8, 2, schedule);

        assertThat(report.isValid()).isFalse();
        assertThat(report.hardViolations()).anyMatch(v -> v.contains("больше одного раза"));
    }

    @Test
    void matchWithDuplicatePlayer_isHardViolation() {
        List<List<MatchSlot>> schedule = List.of(List.of(
                new MatchSlot(0, 0, 1, 2, 1) // одного игрока дважды в одном матче
        ));

        AmericanoScheduleValidator.ScheduleReport report = AmericanoScheduleValidator.validate(8, 2, schedule);

        assertThat(report.isValid()).isFalse();
        assertThat(report.hardViolations()).anyMatch(v -> v.contains("не 4 уникальных игрока"));
    }

    @Test
    void courtOutOfRange_isHardViolation() {
        List<List<MatchSlot>> schedule = List.of(List.of(
                new MatchSlot(0, 1, 2, 3, 5) // всего 2 корта, назначен 5-й
        ));

        AmericanoScheduleValidator.ScheduleReport report = AmericanoScheduleValidator.validate(8, 2, schedule);

        assertThat(report.isValid()).isFalse();
        assertThat(report.hardViolations()).anyMatch(v -> v.contains("некорректный корт"));
    }

    @Test
    void repeatedPartnerPair_isCountedButNotHardViolation() {
        List<List<MatchSlot>> schedule = List.of(
                List.of(new MatchSlot(0, 1, 2, 3, 1)),
                List.of(new MatchSlot(0, 1, 2, 3, 1)) // 0-1 и 2-3 повторили партнёрство
        );

        AmericanoScheduleValidator.ScheduleReport report = AmericanoScheduleValidator.validate(4, 1, schedule);

        assertThat(report.isValid()).isTrue(); // не структурное нарушение раунда
        assertThat(report.partnerRepeats()).isEqualTo(2); // пара (0,1) и пара (2,3)
    }

    @Test
    void consecutiveSameCourt_isTrackedAcrossRoundsPerPlayer() {
        List<List<MatchSlot>> schedule = List.of(
                List.of(new MatchSlot(0, 1, 2, 3, 1)),
                List.of(new MatchSlot(0, 2, 1, 3, 1)),
                List.of(new MatchSlot(0, 3, 1, 2, 1))
        );

        AmericanoScheduleValidator.ScheduleReport report = AmericanoScheduleValidator.validate(4, 1, schedule);

        assertThat(report.maxConsecutiveSameCourt()).isEqualTo(3);
    }

    @Test
    void courtsNeverVisited_countsPlayerCourtCombinationsWithZeroVisits() {
        // 2 корта заявлены, но все матчи все 3 раунда идут на корте 1 — корт 2 никто не посетил.
        List<List<MatchSlot>> schedule = List.of(
                List.of(new MatchSlot(0, 1, 2, 3, 1)),
                List.of(new MatchSlot(0, 2, 1, 3, 1))
        );

        AmericanoScheduleValidator.ScheduleReport report = AmericanoScheduleValidator.validate(4, 2, schedule);

        assertThat(report.courtsNeverVisited()).isEqualTo(4); // 4 игрока × корт 2, на котором никто не был
    }

    @Test
    void opponentMeetings_minAndMaxComputedAcrossAllPairs() {
        List<List<MatchSlot>> schedule = List.of(
                List.of(new MatchSlot(0, 1, 2, 3, 1)) // соперники: (0,2)(0,3)(1,2)(1,3) = 1 встреча; (0,1)(2,3) партнёры, 0 встреч как соперники
        );

        AmericanoScheduleValidator.ScheduleReport report = AmericanoScheduleValidator.validate(4, 1, schedule);

        assertThat(report.minOpponentMeetings()).isZero(); // пара (0,1) никогда не соперники в этом расписании
        assertThat(report.maxOpponentMeetings()).isEqualTo(1);
    }
}
