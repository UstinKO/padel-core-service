package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.AmericanoPlayerStatus;
import com.padle.core.padelcoreservice.model.enums.PlayoffStage;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public final class TeamQualificationScheduling {
    private final AmericanoTeamRepository teams;
    private final AmericanoMatchRepository matches;

    record Snapshot(List<AmericanoTeam> teams, List<QualificationSchedule.Team> inputs,
                    List<QualificationSchedule.Meeting> meetings, Map<Long, TeamWaiting.Waiting> waiting) {}
    public record Preview(boolean safe, boolean repeat, List<Long> affectedTeams, String reason) {}

    Snapshot snapshot(long tournamentId, Long excludedMatch) {
        var roster = teams.findByTournamentIdAndStatus(tournamentId, AmericanoPlayerStatus.ACTIVE);
        var all = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId).stream()
                .filter(m -> !Objects.equals(excludedMatch, m.getId())).toList();
        var qualification = all.stream().filter(m -> m.getRound().getPhase() == TournamentPhase.QUALIFICATION).toList();
        var now = LocalDateTime.now();
        var waiting = roster.stream().collect(Collectors.toMap(AmericanoTeam::getId,
                t -> waiting(t.getId(), all, now)));
        var inputs = roster.stream().map(t -> {
            var played = qualification.stream().filter(m -> contains(m, t.getId())).toList();
            var first = played.stream().findFirst();
            Boolean won = first.isEmpty() || !first.orElseThrow().isCompleted() ? null
                    : first.orElseThrow().getTeam1Id().equals(t.getId()) == first.orElseThrow().isTeam1Winner();
            boolean ready = Boolean.TRUE.equals(t.getAttended()) && played.size() < 2
                    && all.stream().filter(m -> contains(m, t.getId())).noneMatch(AmericanoMatch::isActive);
            return new QualificationSchedule.Team(t.getId(), t.getTeamNumber(), ready, won,
                    Objects.requireNonNullElse(waiting.get(t.getId()).currentSeconds(), 0L));
        }).toList();
        var fixtures = qualification.stream().filter(m -> m.getTeam1Id() != null && m.getTeam2Id() != null)
                .map(m -> new QualificationSchedule.Meeting(m.getTeam1Id(), m.getTeam2Id())).toList();
        return new Snapshot(roster, inputs, fixtures, waiting);
    }

    Preview preview(long tournamentId, long first, long second, Long excludedMatch) {
        var state = snapshot(tournamentId, excludedMatch);
        var result = QualificationSchedule.check(state.inputs(), state.meetings(),
                new QualificationSchedule.Meeting(first, second));
        var numbers = state.teams().stream().filter(t -> result.stranded().contains(t.getId()))
                .map(t -> t.getTeamNumber().longValue()).sorted().toList();
        return new Preview(result.safe(), result.repeat(), numbers,
                result.safe() ? "safe" : result.repeat() ? "repeat" : "remainder");
    }

    void validate(long tournamentId, long first, long second, Long excludedMatch, boolean override, String reason) {
        var result = preview(tournamentId, first, second, excludedMatch);
        if (!result.safe() && (!override || reason == null || reason.isBlank() || reason.length() > 500)) {
            throw new InvalidStateException("team_playoff.schedule." + result.reason());
        }
    }

    static TeamWaiting.Waiting waiting(long teamId, List<AmericanoMatch> all, LocalDateTime now) {
        var intervals = all.stream().filter(m -> contains(m, teamId)).map(m -> {
            boolean lost = m.isCompleted() && m.getPlayoffStage() != null
                    && (m.getTeam1Id().equals(teamId) != m.isTeam1Winner());
            boolean terminal = lost || m.getPlayoffStage() == PlayoffStage.FINAL;
            return new TeamWaiting.Interval(m.getId(), m.getStartedAt(), m.isCompleted() ? m.getCompletedAt() : null, terminal);
        }).toList();
        return TeamWaiting.calculate(intervals, now);
    }

    private static boolean contains(AmericanoMatch match, long teamId) {
        return Objects.equals(match.getTeam1Id(), teamId) || Objects.equals(match.getTeam2Id(), teamId);
    }
}
