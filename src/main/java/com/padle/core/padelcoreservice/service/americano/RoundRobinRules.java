package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.RoundRobinStandingDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoConfigDto;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.mapper.americano.RoundRobinPointsStandingsMapper;
import com.padle.core.padelcoreservice.model.americano.*;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import java.util.*;
import java.util.stream.IntStream;
import org.mapstruct.factory.Mappers;

/** Правила счёта и мест кругового этапа, независимые от хранения. */
public final class RoundRobinRules {
    private static final RoundRobinPointsStandingsMapper POINTS_MAPPER = Mappers.getMapper(RoundRobinPointsStandingsMapper.class);
    private RoundRobinRules() {}

    public static RoundRobinScoringMode mode(AmericanoRound round) {
        return Objects.requireNonNullElse(round.getScoringMode(), RoundRobinScoringMode.POINTS);
    }

    public static boolean additional(AmericanoMatch match) {
        return match.getRound().getPhase() == TournamentPhase.PLACEMENT;
    }

    public static void validateConfig(TeamAmericanoConfigDto config) {
        if (config.getCourts() == null || config.getCourts() < 1 || config.getCourts() > 10
                || config.getScoringMode() == null) fail("config");
        if (config.getScoringMode() == RoundRobinScoringMode.POINTS
                && (config.getPointsPerMatch() == null || config.getPointsPerMatch() < 1
                || config.getPointsPerMatch() > 1000)) fail("config");
    }

    public static void validateScore(AmericanoMatch match, int first, int second) {
        if (first < 0 || second < 0) fail("score");
        if (mode(match.getRound()) == RoundRobinScoringMode.SETS) {
            if (Math.max(first, second) != 6 || Math.min(first, second) > 5) fail("set_score");
        } else if ((long) first + second != match.getRound().getPointsPerMatch()) {
            fail("points_score");
        }
        if (additional(match) && match.getPlacementStart() != null && first == second) fail("draw");
    }

    public static void apply(AmericanoTeam team, int scored, int conceded, RoundRobinScoringMode mode, int direction) {
        if (mode == RoundRobinScoringMode.POINTS) {
            if (direction > 0) team.addMatchResult(scored, conceded);
            else team.revertMatchResult(scored, conceded);
            return;
        }
        if (direction > 0) team.addSetMatchResult(scored, conceded);
        else team.revertSetMatchResult(scored, conceded);
        team.setPointsScored(team.getPointsScored() + direction * scored);
        team.setPointsConceded(team.getPointsConceded() + direction * conceded);
        team.setTotalScore(team.getTotalScore() + direction * scored);
    }

    public static Comparator<AmericanoTeam> order(RoundRobinScoringMode mode) {
        if (mode == RoundRobinScoringMode.SETS) {
            return Comparator.comparing(AmericanoTeam::getMatchesWon).reversed()
                    .thenComparing(Comparator.comparing(AmericanoTeam::getGameDifference).reversed())
                    .thenComparing(AmericanoTeam::getTeamNumber).thenComparing(AmericanoTeam::getId);
        }
        var pointsOrder = RoundRobinPointsStandingsEngine.order();
        return (first, second) -> pointsOrder.compare(POINTS_MAPPER.toTeam(first, 0), POINTS_MAPPER.toTeam(second, 0));
    }

    public static List<RoundRobinStandingDto> finalOrder(List<RoundRobinStandings.Ranked> circle, List<AmericanoMatch> extra) {
        Map<Integer, Long> assigned = new HashMap<>();
        extra.stream().filter(AmericanoMatch::isCompleted).filter(m -> m.getPlacementStart() != null)
                .forEach(m -> {
                    boolean firstWon = m.getTeam1Score() > m.getTeam2Score();
                    assigned.put(m.getPlacementStart(), firstWon ? m.getTeam1Id() : m.getTeam2Id());
                    assigned.put(m.getPlacementStart() + 1, firstWon ? m.getTeam2Id() : m.getTeam1Id());
                });
        var remaining = circle.stream().filter(t -> !assigned.containsValue(t.team().getId())).toList();
        var slots = IntStream.rangeClosed(1, circle.size()).filter(p -> !assigned.containsKey(p)).boxed().toList();
        var tiedPlaces = IntStream.range(0, remaining.size()).boxed().collect(java.util.stream.Collectors.toMap(
                i -> remaining.get(i).position(), slots::get, Math::min));
        var bySlot = IntStream.range(0, remaining.size()).boxed().collect(java.util.stream.Collectors.toMap(slots::get, remaining::get));
        var teams = circle.stream().collect(java.util.stream.Collectors.toMap(t -> t.team().getId(), t -> t.team()));
        return IntStream.rangeClosed(1, circle.size()).mapToObj(position -> {
            var team = assigned.containsKey(position) ? teams.get(assigned.get(position)) : bySlot.get(position).team();
            int place = assigned.containsKey(position) ? position : tiedPlaces.get(bySlot.get(position).position());
            return new RoundRobinStandingDto(team.getId(), team.getTeamNumber(), team.getDisplayName(), place);
        }).toList();
    }

    public static void fail(String reason) {
        throw new InvalidStateException("round_robin.error." + reason);
    }
}
