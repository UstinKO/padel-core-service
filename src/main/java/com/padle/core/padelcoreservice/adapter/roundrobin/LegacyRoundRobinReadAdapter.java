package com.padle.core.padelcoreservice.adapter.roundrobin;

import com.padle.core.application.roundrobin.RoundRobinReadPort;
import com.padle.core.application.roundrobin.RoundRobinReadSet;
import com.padle.core.padelcoreservice.mapper.americano.RoundRobinReadModelMapper;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyRoundRobinReadAdapter implements RoundRobinReadPort {
    private final TournamentRepository tournaments;
    private final AmericanoTeamRepository teams;
    private final AmericanoRoundRepository rounds;
    private final AmericanoMatchRepository matches;
    private final RoundRobinReadModelMapper mapper;

    @Override
    public Optional<RoundRobinReadSet> findByTournamentId(Long tournamentId) {
        return tournaments.findById(tournamentId).map(tournament -> fromLoaded(tournament,
                teams.findRankingByTournamentId(tournamentId), rounds.findByTournamentIdOrderByRoundNumberAsc(tournamentId)));
    }

    /** initialize передаёт свои сохранённые объекты в той же write-транзакции. */
    public RoundRobinReadSet fromLoaded(Tournament tournament, List<AmericanoTeam> rankingTeams,
                                       List<AmericanoRound> loadedRounds) {
        var allMatches = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournament.getId());
        var rankingValues = rankingTeams.stream().map(mapper::toTeam).toList();
        var roundValues = loadedRounds.stream().map(mapper::toRound).toList();
        var matchValues = allMatches.stream().map(mapper::toMatch).toList();
        var additionalTeams = teams.findByTournamentId(tournament.getId());
        var additionalMatches = matches.findByTournamentIdAndPhase(tournament.getId(), TournamentPhase.PLACEMENT);
        return new RoundRobinReadSet(mapper.toEvent(tournament), rankingValues, roundValues, matchValues,
                additionalTeams.stream().map(mapper::toTeam).toList(),
                additionalMatches.stream().map(mapper::toMatch).toList());
    }
}
