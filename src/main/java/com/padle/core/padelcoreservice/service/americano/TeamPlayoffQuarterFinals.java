package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.padelcoreservice.model.enums.PlayoffStage;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
final class TeamPlayoffQuarterFinals {
    private final AmericanoMatchRepository matches;
    private final AmericanoTeamRepository teams;
    private final PlayoffMatchingEngine engine;

    void refresh(long tournamentId) {
        var quarters = matches.findByTournamentIdAndPlayoffStage(tournamentId, PlayoffStage.QUARTER_FINAL);
        var sources = slots(tournamentId);
        if (quarters.size() != 4 || sources.size() != 8) return;
        var fixed = locked(quarters);
        var opponents = opponents(tournamentId);
        var used = fixed.stream().flatMap(p -> java.util.stream.Stream.of(p.first(), p.second())).collect(Collectors.toSet());
        var ready = sources.stream().filter(s -> s.size() == 1).map(s -> s.get(0))
                .filter(id -> !used.contains(id)).toList();
        var ranked = teams.findPlayoffRankingByTournamentId(tournamentId);
        var seed = new HashMap<Long, Integer>();
        for (int i = 0; i < ranked.size(); i++) seed.put(ranked.get(i).getId(), i + 1);
        var candidates = ready.stream().map(id -> new PlayoffMatchingEngine.Candidate(id, seed.get(id),
                ranked.stream().filter(t -> t.getId().equals(id)).findFirst().orElseThrow().getMatchesWon())).toList();
        var choices = new ArrayList<QualificationCompletion.Pair>();
        if (ready.size() % 2 == 0) {
            engine.match(candidates, opponents, PlayoffMatchingEngine.PriorityOrder.REMATCH_FIRST).pairings()
                    .forEach(p -> choices.add(QualificationCompletion.Pair.of(p.team1Id(), p.team2Id())));
        }
        for (int i = 0; i < ready.size(); i++) {
            for (int j = i + 1; j < ready.size(); j++) {
                var pair = QualificationCompletion.Pair.of(ready.get(i), ready.get(j));
                if (!choices.contains(pair)) choices.add(pair);
            }
        }
        var selected = new ArrayList<QualificationCompletion.Pair>();
        for (var pair : choices) {
            if (used.contains(pair.first()) || used.contains(pair.second())) continue;
            var test = new HashSet<>(fixed);
            test.addAll(selected);
            test.add(pair);
            if (!PlayoffContinuation.safe(sources, test, opponents)) continue;
            selected.add(pair);
            used.add(pair.first());
            used.add(pair.second());
        }
        var drafts = quarters.stream().filter(m -> !isLocked(m)).sorted(Comparator.comparing(AmericanoMatch::getMatchNumber)).toList();
        var byId = ranked.stream().collect(Collectors.toMap(AmericanoTeam::getId, t -> t));
        for (int i = 0; i < drafts.size(); i++) {
            var match = drafts.get(i);
            if (i < selected.size()) fill(match, byId.get(selected.get(i).first()), byId.get(selected.get(i).second()));
            else clear(match);
        }
        matches.saveAll(drafts);
        if (!selected.isEmpty()) drafts.get(0).getRound().setStatus(AmericanoRoundStatus.IN_PROGRESS);
        var destinations = new HashMap<Long, AmericanoMatch>();
        quarters.stream().filter(m -> m.getTeam1Id() != null && m.getTeam2Id() != null).forEach(m -> {
            destinations.put(m.getTeam1Id(), m);
            destinations.put(m.getTeam2Id(), m);
        });
        var incoming = matches.findByTournamentIdAndPlayoffStage(tournamentId, PlayoffStage.ROUND_OF_16);
        for (var source : incoming) {
            Long winner = source.isCompleted() ? (source.isTeam1Winner() ? source.getTeam1Id() : source.getTeam2Id()) : null;
            var destination = destinations.get(winner);
            source.setNextMatchId(destination == null ? null : destination.getId());
            source.setNextMatchSlot(destination == null ? null : destination.getTeam1Id().equals(winner) ? 1 : 2);
        }
        matches.saveAll(incoming);
    }

    void validateConfirmation(AmericanoMatch proposed) {
        if (proposed.getPlayoffStage() != PlayoffStage.QUARTER_FINAL) return;
        long tournamentId = proposed.getTournament().getId();
        var quarters = matches.findByTournamentIdAndPlayoffStage(tournamentId, PlayoffStage.QUARTER_FINAL);
        if (slots(tournamentId).isEmpty()) return;
        var fixed = locked(quarters);
        fixed.add(QualificationCompletion.Pair.of(proposed.getTeam1Id(), proposed.getTeam2Id()));
        if (!PlayoffContinuation.safe(slots(tournamentId), fixed, opponents(tournamentId))
                && (proposed.getPairingOverrideReason() == null || proposed.getPairingOverrideReason().isBlank())) {
            throw new InvalidStateException("team_playoff.schedule.playoff_remainder");
        }
    }

    boolean safeChanges(long tournamentId, Set<Long> replaced, Set<QualificationCompletion.Pair> proposed) {
        var sources = slots(tournamentId);
        if (sources.isEmpty()) return true;
        var current = matches.findByTournamentIdAndPlayoffStage(tournamentId, PlayoffStage.QUARTER_FINAL).stream()
                .filter(m -> !replaced.contains(m.getId())).toList();
        var fixed = locked(current);
        fixed.addAll(proposed);
        return PlayoffContinuation.safe(sources, fixed, opponents(tournamentId));
    }

    private List<List<Long>> slots(long tournamentId) {
        var first = matches.findByTournamentIdAndPlayoffStage(tournamentId, PlayoffStage.ROUND_OF_16);
        if (first.isEmpty()) return List.of();
        var ranked = teams.findPlayoffRankingByTournamentId(tournamentId);
        var result = new ArrayList<List<Long>>();
        ranked.stream().limit(PlayoffFormat.directToQuarterFinal(ranked.size())).forEach(t -> result.add(List.of(t.getId())));
        for (var match : first) {
            if (match.getTeam1Id() == null || match.getTeam2Id() == null) return List.of();
            result.add(match.isCompleted() ? List.of(match.isTeam1Winner() ? match.getTeam1Id() : match.getTeam2Id())
                    : List.of(match.getTeam1Id(), match.getTeam2Id()));
        }
        return result;
    }

    private Map<Long, Set<Long>> opponents(long tournamentId) {
        var result = new HashMap<Long, Set<Long>>();
        matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId).stream()
                .filter(AmericanoMatch::isCompleted)
                .filter(m -> m.getPlayoffStage() == null || m.getPlayoffStage() == PlayoffStage.ROUND_OF_16).filter(m -> m.getTeam1Id() != null && m.getTeam2Id() != null)
                .forEach(m -> {
                    result.computeIfAbsent(m.getTeam1Id(), id -> new HashSet<>()).add(m.getTeam2Id());
                    result.computeIfAbsent(m.getTeam2Id(), id -> new HashSet<>()).add(m.getTeam1Id());
                });
        return result;
    }

    private Set<QualificationCompletion.Pair> locked(List<AmericanoMatch> quarters) {
        return quarters.stream().filter(this::isLocked).filter(m -> m.getTeam1Id() != null && m.getTeam2Id() != null)
                .map(m -> QualificationCompletion.Pair.of(m.getTeam1Id(), m.getTeam2Id())).collect(Collectors.toSet());
    }

    private boolean isLocked(AmericanoMatch match) {
        return match.isCompleted() || Boolean.TRUE.equals(match.getPairingConfirmed())
                || Boolean.TRUE.equals(match.getPlayoffPlayStarted());
    }

    private void fill(AmericanoMatch match, AmericanoTeam first, AmericanoTeam second) {
        match.setTeam1Id(first.getId());
        match.setTeam1Player1(first.getPlayer1());
        match.setTeam1Player2(first.getPlayer2());
        match.setTeam2Id(second.getId());
        match.setTeam2Player1(second.getPlayer1());
        match.setTeam2Player2(second.getPlayer2());
        match.setNote(first.getDisplayName() + " vs " + second.getDisplayName());
        match.setCourtNumber(null);
        match.setStartedAt(null);
        match.setPlayoffPlayStarted(false);
        match.setStatus(AmericanoRoundStatus.QUEUED);
    }

    private void clear(AmericanoMatch match) {
        match.setTeam1Id(null);
        match.setTeam1Player1(null);
        match.setTeam1Player2(null);
        match.setTeam2Id(null);
        match.setTeam2Player1(null);
        match.setTeam2Player2(null);
        match.setCourtNumber(null);
        match.setStartedAt(null);
        match.setPlayoffPlayStarted(false);
        match.setNote("TBD");
        match.setStatus(AmericanoRoundStatus.PENDING);
    }
}
