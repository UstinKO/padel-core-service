package com.padle.core.padelcoreservice.adapter.roundrobin;

import com.padle.core.application.roundrobin.RoundRobinPosition;
import com.padle.core.application.roundrobin.RoundRobinPositionInput;
import com.padle.core.application.roundrobin.RoundRobinPositionInput.PositionMatch;
import com.padle.core.application.roundrobin.RoundRobinPositionPort;
import com.padle.core.application.roundrobin.RoundRobinReadSet.ScoringMode;
import com.padle.core.padelcoreservice.mapper.americano.RoundRobinPositionMapper;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.RoundRobinScoringMode;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import com.padle.core.padelcoreservice.service.americano.RoundRobinRules;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyRoundRobinPositionAdapter implements RoundRobinPositionPort {
    private final AmericanoRoundRepository rounds;
    private final AmericanoTeamRepository teams;
    private final AmericanoMatchRepository matches;
    private final RoundRobinPositionMapper mapper;
    private final EntityManager entities;

    @Override
    public RoundRobinPositionInput load(Long tournamentId) {
        var mode = mapper.toMode(rounds.findByTournamentIdOrderByRoundNumberAsc(tournamentId).stream()
                .filter(round -> round.getPhase() == null).findFirst().map(RoundRobinRules::mode)
                .orElse(RoundRobinScoringMode.POINTS));
        var loadedTeams = teams.findByTournamentId(tournamentId);
        var loadedMatches = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId);
        var values = loadedTeams.stream().map(team -> mapper.toTeam(team, mode)).toList();
        var meetings = mode == ScoringMode.SETS && !loadedTeams.isEmpty()
                ? loadedMatches.stream().map(mapper::toMatch).toList() : List.<PositionMatch>of();
        return new RoundRobinPositionInput(mode, values, meetings);
    }

    @Override
    public void savePositions(List<RoundRobinPosition> positions) {
        teams.saveAll(positions.stream().map(this::apply).toList());
    }

    private AmericanoTeam apply(RoundRobinPosition position) {
        // load уже материализовал эти команды в том же persistence context; SELECT не требуется.
        var team = entities.getReference(AmericanoTeam.class, position.teamId());
        mapper.apply(position, team);
        return team;
    }
}
