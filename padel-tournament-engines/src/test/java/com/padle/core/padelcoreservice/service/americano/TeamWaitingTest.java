package com.padle.core.padelcoreservice.service.americano;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TeamWaitingTest {
    private final LocalDateTime base = LocalDateTime.of(2026, 11, 28, 14, 0);

    @Test
    void queueDoesNotResetWaitAndActualStartStopsIt() {
        var first = new TeamWaiting.Interval(1, base, base.plusMinutes(20), false);
        var queued = new TeamWaiting.Interval(2, null, null, false);
        var waiting = TeamWaiting.calculate(List.of(first, queued), base.plusMinutes(35));
        assertThat(waiting.currentSeconds()).isEqualTo(900);
        var second = new TeamWaiting.Interval(2, base.plusMinutes(37), base.plusMinutes(57), false);
        var after = TeamWaiting.calculate(List.of(first, second), base.plusMinutes(61));
        assertThat(after.currentSeconds()).isEqualTo(240);
        assertThat(after.maximumSeconds()).isEqualTo(1020);
        assertThat(after.totalSeconds()).isEqualTo(1260);
    }

    @Test
    void legacyUnknownTimeIsNotInventedAndEliminatedTeamDoesNotKeepWaiting() {
        assertThat(TeamWaiting.calculate(List.of(new TeamWaiting.Interval(1, base, null, false)), base.plusHours(1))
                .currentSeconds()).isNull();
        assertThat(TeamWaiting.calculate(List.of(new TeamWaiting.Interval(1, base, base.plusMinutes(20), true)), base.plusHours(1))
                .totalSeconds()).isZero();
    }
}
