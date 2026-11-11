package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto;
import com.padle.core.padelcoreservice.mapper.TeamPlayoffRoundMapper;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamPlayoffReadService {
    private final AmericanoMatchRepository matches;
    private final AmericanoTeamRepository teams;
    private final TeamPlayoffService playoffs;
    private final TeamPlayoffRoundMapper mapper;

    public List<AmericanoRoundDto> rounds(List<AmericanoRound> source) {
        if(source.isEmpty()) return List.of();
        long eventId = source.get(0).getTournament().getId();
        teams.findPlayoffRankingByTournamentId(eventId);
        var byRound = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(eventId).stream()
                .filter(match -> !match.isQueued()).collect(Collectors.groupingBy(match -> match.getRound().getId()));
        return source.stream().map(round -> mapper.round(round,
                byRound.getOrDefault(round.getId(), List.of()).stream().map(playoffs::toMatchDto).toList())).toList();
    }

    public void qualificationOrdinals(Long eventId, List<AmericanoRoundDto> rounds) {
        var ordinals = playoffs.computeQualMatchOrdinals(eventId);
        rounds.forEach(round -> round.getMatches().forEach(match -> {
            var values = ordinals.get(match.getId());
            if(values != null) {
                match.setTeam1MatchOrdinal(values[0] > 0 ? values[0] : null);
                match.setTeam2MatchOrdinal(values[1] > 0 ? values[1] : null);
            }
        }));
    }

    public List<AmericanoRoundDto> qualificationWaves(List<AmericanoRoundDto> rounds) {
        if(rounds.isEmpty()) return rounds;
        var container = rounds.get(0);
        var byWave = container.getMatches().stream().collect(Collectors.groupingBy(match -> Math.max(
                Objects.requireNonNullElse(match.getTeam1MatchOrdinal(),1), Objects.requireNonNullElse(match.getTeam2MatchOrdinal(),1)), TreeMap::new, Collectors.toList()));
        return byWave.entrySet().stream().map(entry -> mapper.wave(container, entry.getKey(), entry.getValue().stream()
                .sorted(Comparator.comparing(AmericanoMatchDto::getCourtNumber, Comparator.nullsLast(Comparator.naturalOrder()))).toList())).toList();
    }
}
