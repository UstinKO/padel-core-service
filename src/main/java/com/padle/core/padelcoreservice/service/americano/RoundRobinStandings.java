package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.mapper.americano.RoundRobinSetStandingsMapper;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import java.util.List;
import java.util.stream.IntStream;
import org.mapstruct.factory.Mappers;

/** Круговые места; мини-таблица строится только внутри группы с равными общими показателями. */
public final class RoundRobinStandings {
    private static final RoundRobinSetStandingsMapper MAPPER = Mappers.getMapper(RoundRobinSetStandingsMapper.class);

    private RoundRobinStandings() {}

    public record Ranked(AmericanoTeam team, int position) {}
    public static List<Ranked> rank(List<AmericanoTeam> teams, List<AmericanoMatch> matches,
                                    RoundRobinScoringMode mode) {
        if (mode == RoundRobinScoringMode.POINTS) {
            var ordered = teams.stream().sorted(RoundRobinRules.order(mode)).toList();
            return IntStream.range(0, ordered.size()).mapToObj(i -> new Ranked(ordered.get(i), i + 1)).toList();
        }
        if (teams.isEmpty()) { return List.of(); }
        var inputs = IntStream.range(0, teams.size()).mapToObj(index -> MAPPER.toTeam(teams.get(index), index)).toList();
        var completed = matches.stream().filter(AmericanoMatch::isCompleted)
                .filter(match -> match.getRound().getPhase() == null
                        && match.getTeam1Score() != null && match.getTeam2Score() != null)
                .map(MAPPER::toMatch).toList();
        return RoundRobinSetStandingsEngine.rank(inputs, completed).stream()
                .map(ranked -> MAPPER.toRanked(ranked, teams)).toList();
    }
}
