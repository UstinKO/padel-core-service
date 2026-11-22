package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.*;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.*;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.*;
import com.padle.core.padelcoreservice.service.conduct.ConductChange;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.padle.core.padelcoreservice.service.americano.RoundRobinRules.fail;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoundRobinAdditionalMatches {
    private final TournamentRepository tournaments;
    private final AmericanoRoundRepository rounds;
    private final AmericanoMatchRepository matches;
    private final AmericanoTeamRepository teams;

    @Transactional
    @ConductChange(ConductChange.Action.CREATE_MATCH)
    public AmericanoMatch add(Long tournamentId, RoundRobinAdditionalMatchRequest request) {
        var tournament = tournaments.lockById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found"));
        var all = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId);
        var previous = all.stream().filter(m -> request.requestId().equals(m.getRoundRobinRequestId())).findFirst();
        if (previous.isPresent()) {
            var saved = previous.orElseThrow();
            if (!Objects.equals(saved.getTeam1Id(), request.team1Id())
                    || !Objects.equals(saved.getTeam2Id(), request.team2Id())
                    || !Objects.equals(saved.getCourtNumber(), request.courtNumber())
                    || !Objects.equals(saved.getPlacementStart(), request.placementStart())) fail("request_conflict");
            return saved;
        }
        var circle = rounds.findByTournamentIdOrderByRoundNumberAsc(tournamentId).stream()
                .filter(r -> r.getPhase() == null).toList();
        if (circle.isEmpty() || circle.stream().anyMatch(r -> !r.isCompleted())) fail("unfinished_circle");
        if (tournament.getEstado() == TournamentStatus.FINALIZADO
                || tournament.getEstado() == TournamentStatus.CANCELADO) fail("finished");
        var config = circle.get(0);
        var first = team(tournamentId, request.team1Id());
        var second = team(tournamentId, request.team2Id());
        validateRequest(request, first, second, config, all);
        return save(tournament, request, first, second, config);
    }

    private AmericanoTeam team(Long tournamentId, Long id) {
        return teams.findById(id).filter(t -> t.getTournament().getId().equals(tournamentId))
                .filter(t -> t.getStatus() == AmericanoPlayerStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));
    }

    private void validateRequest(RoundRobinAdditionalMatchRequest request, AmericanoTeam first,
                                 AmericanoTeam second, AmericanoRound config, List<AmericanoMatch> all) {
        if (first.getId().equals(second.getId())) fail("same_team");
        if (request.courtNumber() < 1 || request.courtNumber() > config.getCourts()) fail("court");
        var ids = Set.of(first.getId(), second.getId());
        if (all.stream().filter(RoundRobinRules::additional).filter(AmericanoMatch::isActive)
                .anyMatch(m -> m.getCourtNumber().equals(request.courtNumber())
                        || ids.contains(m.getTeam1Id()) || ids.contains(m.getTeam2Id()))) fail("busy");
        if (request.placementStart() == null) return;
        var count = teams.findByTournamentId(config.getTournament().getId()).size();
        if (request.placementStart() < 1 || request.placementStart() >= count) fail("placement");
        if (all.stream().filter(RoundRobinRules::additional).filter(m -> m.getPlacementStart() != null)
                .anyMatch(m -> Math.abs(m.getPlacementStart() - request.placementStart()) <= 1
                        || ids.contains(m.getTeam1Id()) || ids.contains(m.getTeam2Id()))) fail("placement_conflict");
    }

    private AmericanoMatch save(Tournament tournament, RoundRobinAdditionalMatchRequest request,
                               AmericanoTeam first, AmericanoTeam second, AmericanoRound config) {
        var round = AmericanoRound.builder().tournament(tournament)
                .roundNumber(rounds.findMaxRoundNumber(tournament.getId()).orElseThrow() + 1)
                .phase(TournamentPhase.PLACEMENT).scoringMode(RoundRobinRules.mode(config))
                .pointsPerMatch(config.getPointsPerMatch()).courts(config.getCourts()).isDoubles(true).build();
        round.start();
        rounds.save(round);
        var match = AmericanoMatch.builder().round(round).tournament(tournament).matchNumber(1)
                .team1Id(first.getId()).team2Id(second.getId()).isDoubles(true)
                .team1Player1(first.getPlayer1()).team1Player2(first.getPlayer2())
                .team2Player1(second.getPlayer1()).team2Player2(second.getPlayer2())
                .courtNumber(request.courtNumber()).placementStart(request.placementStart())
                .note(Objects.requireNonNullElse(request.label(), "").strip())
                .roundRobinRequestId(request.requestId()).build();
        match.start();
        matches.save(match);
        round.setMatches(new ArrayList<>(List.of(match)));
        return match;
    }

    public List<RoundRobinAdditionalMatchDto> list(Long tournamentId) {
        var indexed = teams.findByTournamentId(tournamentId).stream()
                .collect(java.util.stream.Collectors.toMap(AmericanoTeam::getId, t -> t));
        return matches.findByTournamentIdAndPhase(tournamentId, TournamentPhase.PLACEMENT).stream().map(m -> {
            var first = indexed.get(m.getTeam1Id());
            var second = indexed.get(m.getTeam2Id());
            return new RoundRobinAdditionalMatchDto(m.getId(), m.getPlacementStart(), m.getNote(), m.getCourtNumber(),
                    first.getId(), first.getTeamNumber(), first.getDisplayName(),
                    second.getId(), second.getTeamNumber(), second.getDisplayName(),
                    m.getTeam1Score(), m.getTeam2Score(), m.getStatus());
        }).toList();
    }
}
