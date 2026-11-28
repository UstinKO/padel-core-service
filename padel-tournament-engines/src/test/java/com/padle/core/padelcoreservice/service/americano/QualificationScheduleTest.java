package com.padle.core.padelcoreservice.service.americano;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class QualificationScheduleTest {
    @Test
    void rejectsJointTrapEvenWhenBothAssignmentsWereIndividuallySafe() {
        var teams = roster(10);
        var meetings = firstMeetings(10);
        meetings.add(new QualificationSchedule.Meeting(1, 3));
        meetings.add(new QualificationSchedule.Meeting(2, 4));
        assertThat(check(teams, meetings, 5, 7).safe()).isTrue();
        assertThat(check(teams, meetings, 6, 8).safe()).isTrue();
        meetings.add(new QualificationSchedule.Meeting(5, 7));
        assertThat(check(teams, meetings, 6, 8).safe()).isFalse();
        assertThat(check(teams, meetings, 9, 10).repeat()).isTrue();
    }

    @Test
    void acceptsPositiveTwelveInTheRequiredOrder() {
        var meetings = firstMeetings(12);
        for (var pair : List.of(new int[]{2, 4}, new int[]{6, 8}, new int[]{1, 3},
                new int[]{5, 7}, new int[]{9, 11}, new int[]{10, 12})) {
            assertThat(check(roster(12), meetings, pair[0], pair[1]).safe()).isTrue();
            meetings.add(new QualificationSchedule.Meeting(pair[0], pair[1]));
        }
    }

    @Test
    void longWaitingWinnersPrecedeRecentlyFinishedLosers() {
        var teams = roster(12).stream().map(t -> new QualificationSchedule.Team(t.id(), t.number(), true,
                t.number() % 2 == 1, t.number() % 2 == 1 ? 1200 : 300)).toList();
        var suggestions = QualificationSchedule.suggest(teams, firstMeetings(12));
        assertThat(suggestions.get(0).first() % 2).isEqualTo(1);
        teams = roster(12).stream().map(t -> new QualificationSchedule.Team(t.id(), t.number(), true,
                t.number() % 2 == 1, t.number() % 2 == 1 ? 590 : 300)).toList();
        suggestions = QualificationSchedule.suggest(teams, firstMeetings(12));
        assertThat(suggestions.get(0).first() % 2).isZero();
    }

    @ParameterizedTest
    @CsvSource({"8,4", "8,5", "9,4", "9,5", "10,4", "10,5", "11,4", "11,5", "12,4", "12,5",
            "13,4", "13,5", "14,4", "14,5", "15,4", "15,5", "16,4", "16,5"})
    void asynchronousArrivalAndDurationsAlwaysFinishWithoutRepeats(int count, int courts) {
        for (int scenario = 0; scenario < 40; scenario++) simulate(count, courts, scenario);
    }

    private void simulate(int count, int courts, int scenario) {
        var random = new Random(scenario);
        var meetings = new ArrayList<QualificationSchedule.Meeting>();
        var active = new HashMap<QualificationSchedule.Meeting, Integer>();
        var won = new HashMap<Long, Boolean>();
        var ended = new HashMap<Long, Integer>();
        var arrival = new HashMap<Long, Integer>();
        for (long id = 1; id <= count; id++) arrival.put(id, scenario % 4 == 0 ? 0 : random.nextInt(21));
        int now = 0;
        for (int step = 0; step < 500; step++) {
            final int tick = now;
            var finished = active.entrySet().stream().filter(e -> e.getValue() <= tick).map(Map.Entry::getKey).toList();
            for (var pair : finished) {
                boolean firstWon = scenario % 2 == 0;
                won.putIfAbsent(pair.first(), firstWon);
                won.putIfAbsent(pair.second(), !firstWon);
                ended.put(pair.first(), tick);
                ended.put(pair.second(), tick);
                active.remove(pair);
            }
            var busy = new HashSet<Long>();
            active.keySet().forEach(p -> { busy.add(p.first()); busy.add(p.second()); });
            var inputs = IntStream.rangeClosed(1, count).mapToObj(id -> new QualificationSchedule.Team(id, id,
                    arrival.get((long) id) <= tick && !busy.contains((long) id), won.get((long) id),
                    Math.max(0, tick - ended.getOrDefault((long) id, tick)) * 60L)).toList();
            var proposals = QualificationSchedule.suggest(inputs, meetings);
            for (var proposal : proposals) {
                if (active.size() >= courts) break;
                var pair = new QualificationSchedule.Meeting(proposal.first(), proposal.second());
                assertThat(QualificationSchedule.check(inputs, meetings, pair).safe()).isTrue();
                meetings.add(pair);
                active.put(pair, tick + (scenario % 5 == 0 ? 10 : 5 + random.nextInt(24)));
            }
            if (meetings.size() == count && active.isEmpty()) {
                assertThat(meetings.stream().map(p -> QualificationCompletion.Pair.of(p.first(), p.second())).distinct().count())
                        .isEqualTo(count);
                return;
            }
            now++;
        }
        throw new AssertionError("Квалификация не завершилась: " + count + "/" + courts + "/" + scenario + " " + meetings);
    }

    private QualificationSchedule.Check check(List<QualificationSchedule.Team> teams,
                                                List<QualificationSchedule.Meeting> played, long first, long second) {
        return QualificationSchedule.check(teams, played, new QualificationSchedule.Meeting(first, second));
    }

    private List<QualificationSchedule.Team> roster(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(id ->
                new QualificationSchedule.Team(id, id, true, null, 0)).toList();
    }

    private ArrayList<QualificationSchedule.Meeting> firstMeetings(int count) {
        return IntStream.range(0, count / 2).mapToObj(i -> new QualificationSchedule.Meeting(2L * i + 1, 2L * i + 2))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
}
