package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.AmericanoPlayerStatus;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import com.padle.core.padelcoreservice.service.WebSocketService;
import com.padle.core.padelcoreservice.service.conduct.ConductChange;
import com.padle.core.padelcoreservice.util.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayoffPairExchange {
    private static final String MESSAGE = "admin.playoff.swap.";

    private final AmericanoMatchRepository matches;
    private final AmericanoTeamRepository teams;
    private final TeamPlayoffService playoff;
    private final WebSocketService webSocket;

    public record Team(Long id, Integer number, String name) {}
    public record Pair(Long matchId, Integer matchNumber, Integer court, Team first, Team second,
                       Integer firstGames, Integer secondGames) {}
    public record Encounter(String stage, Integer round, Pair pair) {}
    public record Option(Long matchId, int slot, Team team, Pair current, String block,
                         Pair firstAfter, Pair secondAfter, List<Encounter> encounters, boolean confirmNotStarted,
                         boolean firstScoreRequired, boolean secondScoreRequired) {}
    public record Options(Pair current, String block, List<Option> candidates, List<Pair> dependentMatches) {}
    public record Request(int slot, Long otherMatchId, int otherSlot,
                          boolean confirmNotStarted, boolean confirmRematch) {}
    public record Score(Integer firstGames, Integer secondGames) {}
    public record Correction(Request exchange, boolean confirmCorrection, Score firstScore, Score secondScore) {}
    public record Outcome(Pair firstBefore, Pair secondBefore, Pair firstAfter, Pair secondAfter,
                          boolean rematchConfirmed) {}

    public Options options(Long matchId, int slot, boolean correction) {
        validateSlot(slot);
        State state = state(matchId);
        Pair current = state.pair(state.source());
        String block = block(state.source(), correction);
        if (block != null) return new Options(current, block, List.of(), List.of());
        var candidates = state.all().stream()
                .filter(m -> !m.getId().equals(matchId) && m.getPlayoffStage() == state.source().getPlayoffStage())
                .flatMap(m -> Stream.of(candidate(state, slot, m, 1, correction), candidate(state, slot, m, 2, correction)))
                .toList();
        return new Options(current, null, candidates, correction
                ? laterStageMatches(state).map(state::pair).toList() : List.of());
    }

    @Transactional
    @ConductChange(value = ConductChange.Action.PLAYOFF_SWAP, subject = ConductChange.Subject.AMERICANO_MATCH)
    public Outcome exchange(Long matchId, Request request) {
        return apply(matchId, request, null);
    }

    @Transactional
    @ConductChange(value = ConductChange.Action.PLAYOFF_PAIR_CORRECTED, subject = ConductChange.Subject.AMERICANO_MATCH)
    public Outcome correct(Long matchId, Correction correction) {
        if (!correction.confirmCorrection() || correction.exchange() == null) throw invalid("confirm_correction");
        return apply(matchId, correction.exchange(), correction);
    }

    private Outcome apply(Long matchId, Request request, Correction correction) {
        validateSlot(request.slot());
        validateSlot(request.otherSlot());
        State state = state(matchId);
        AmericanoMatch other = state.all().stream()
                .filter(m -> m.getId().equals(request.otherMatchId()) && !m.getId().equals(matchId)
                        && m.getPlayoffStage() == state.source().getPlayoffStage())
                .findFirst().orElseThrow(() -> invalid("other_match"));
        Option option = candidate(state, request.slot(), other, request.otherSlot(), correction != null);
        if (option.block() != null) throw new InvalidStateException(option.block());
        if (option.confirmNotStarted() && !request.confirmNotStarted()) throw invalid("confirm_not_started");
        if (!option.encounters().isEmpty() && !request.confirmRematch()) throw invalid("confirm_rematch");
        if (correction != null) {
            validateScore(option.firstScoreRequired(), correction.firstScore());
            validateScore(option.secondScoreRequired(), correction.secondScore());
        }

        Pair before = state.pair(state.source());
        Pair otherBefore = option.current();
        List<AmericanoMatch> links = moveLinks(state, request.slot(), other, request.otherSlot());
        updatePair(state, state.source(), option.firstAfter());
        updatePair(state, other, option.secondAfter());
        if (correction != null) {
            recordScore(state.source(), correction.firstScore());
            recordScore(other, correction.secondScore());
        }
        matches.saveAll(links);
        matches.saveAll(List.of(state.source(), other));
        if (correction != null) playoff.advanceAfterPairCorrection(state.source());
        notifyUpdated(state.source(), other);
        return new Outcome(before, otherBefore, state.pair(state.source()), state.pair(other), !option.encounters().isEmpty());
    }

    private void validateScore(boolean required, Score score) {
        if (!required && score == null) return;
        if (!required || score == null || !validGames(score.firstGames()) || !validGames(score.secondGames())
                || score.firstGames().equals(score.secondGames())) throw invalid("correct_score");
    }

    private static boolean validGames(Integer games) {
        return games != null && games >= 0 && games <= 7;
    }

    private void recordScore(AmericanoMatch match, Score score) {
        if (score == null) return;
        match.setPlayoffPlayStarted(true);
        match.setTeam1Games(score.firstGames());
        match.setTeam2Games(score.secondGames());
        match.complete(score.firstGames() > score.secondGames() ? 1 : 0, score.secondGames() > score.firstGames() ? 1 : 0);
    }

    @Transactional
    @ConductChange(value = ConductChange.Action.PLAYOFF_MATCH_STARTED, subject = ConductChange.Subject.AMERICANO_MATCH)
    public void markStarted(Long matchId) {
        State state = state(matchId);
        AmericanoMatch match = state.source();
        String block = block(match);
        if (block != null) throw new InvalidStateException(block);
        if (match.getCourtNumber() == null || match.getCourtNumber() <= 0) throw invalid("court_required");
        if (!eligible(state, List.of(match.getTeam1Id(), match.getTeam2Id()))) throw invalid("ineligible");
        if (state.all().stream().filter(other -> !other.getId().equals(matchId) && !other.isCompleted()
                        && Boolean.TRUE.equals(other.getPlayoffPlayStarted()))
                .anyMatch(other -> match.getCourtNumber().equals(other.getCourtNumber())
                        || includes(other, match.getTeam1Id()) || includes(other, match.getTeam2Id()))) {
            throw invalid("court_conflict");
        }
        match.setPlayoffPlayStarted(true);
        matches.save(match);
        notifyUpdated(match);
    }

    private State state(Long matchId) {
        AmericanoMatch source = matches.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match not found: " + matchId));
        var tournament = source.getTournament();
        if (source.getPlayoffStage() == null || source.getRound().getPhase() != TournamentPhase.PLAYOFF
                || tournament.getTipo() != TournamentType.AMERICANO_TEAMS) throw invalid("other_match");
        if (tournament.getEstado() == TournamentStatus.FINALIZADO
                || tournament.getEstado() == TournamentStatus.CANCELADO) throw invalid("tournament_closed");
        Long id = tournament.getId();
        return new State(source, matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(id),
                teams.findByTournamentId(id).stream().collect(Collectors.toMap(AmericanoTeam::getId, Function.identity())));
    }

    private Option candidate(State state, int slot, AmericanoMatch other, int otherSlot, boolean correction) {
        Pair current = state.pair(other);
        Team team = teamAt(current, otherSlot);
        Pair firstAfter = replace(state.pair(state.source()), slot, team);
        Pair secondAfter = replace(current, otherSlot, state.team(idAt(state.source(), slot)));
        String blocked = rejection(state, other, firstAfter, secondAfter, correction);
        if (blocked != null) {
            return new Option(other.getId(), otherSlot, team, current, blocked, null, null, List.of(), false, false, false);
        }
        var encounters = state.all().stream()
                .filter(AmericanoMatch::isCompleted)
                .filter(m -> sameTeams(m, firstAfter) || sameTeams(m, secondAfter))
                .map(m -> new Encounter(m.getPlayoffStage() == null ? "QUALIFICATION" : m.getPlayoffStage().name(),
                        m.getRound().getRoundNumber(), state.pair(m)))
                .toList();
        return new Option(other.getId(), otherSlot, team, current, null, firstAfter, secondAfter, encounters,
                needsLegacyConfirmation(state.source()) || needsLegacyConfirmation(other),
                correction && scoredOrStarted(state.source()), correction && scoredOrStarted(other));
    }

    private String rejection(State state, AmericanoMatch other, Pair first, Pair second, boolean correction) {
        String sourceBlock = block(state.source(), correction);
        if (sourceBlock != null) return sourceBlock;
        String otherBlock = block(other, correction);
        if (otherBlock != null) return otherBlock;
        if (other.getRound().getPhase() != TournamentPhase.PLAYOFF) return MESSAGE + "other_match";
        if (correction && !scoredOrStarted(state.source()) && !scoredOrStarted(other)) return MESSAGE + "use_exchange";
        List<Long> affected = Stream.of(first.first(), first.second(), second.first(), second.second()).map(Team::id).toList();
        if (new HashSet<>(affected).size() != 4) return MESSAGE + "duplicate";
        if (!eligible(state, affected)) return MESSAGE + "ineligible";
        if (!uniqueStage(state, other, affected)) return MESSAGE + "duplicate";
        if (!validLinks(state, other) || correction && laterStageMatches(state).findAny().isPresent()) {
            return MESSAGE + "dependency";
        }
        if (courtConflict(state, other, affected)) return MESSAGE + "court_conflict";
        return null;
    }

    private Stream<AmericanoMatch> laterStageMatches(State state) {
        return state.all().stream().filter(match -> match.getPlayoffStage() != null
                && match.getPlayoffStage().ordinal() > state.source().getPlayoffStage().ordinal()
                && (match.getTeam1Id() != null || match.getTeam2Id() != null || scoredOrStarted(match)));
    }

    private boolean eligible(State state, List<Long> affected) {
        return affected.stream().allMatch(id -> Optional.ofNullable(state.teams().get(id))
                .map(team -> team.getStatus() == AmericanoPlayerStatus.ACTIVE).orElse(false))
                && state.all().stream().filter(AmericanoMatch::isCompleted)
                .filter(m -> m.getPlayoffStage() != null && m.getPlayoffStage().ordinal() < state.source().getPlayoffStage().ordinal())
                .map(PlayoffPairExchange::loser).filter(Objects::nonNull).noneMatch(affected::contains);
    }

    private boolean uniqueStage(State state, AmericanoMatch other, List<Long> affected) {
        Set<Long> seen = new HashSet<>(affected);
        return state.all().stream().filter(m -> m.getPlayoffStage() == state.source().getPlayoffStage())
                .filter(m -> !isSelected(m, state.source(), other) && m.getStatus() != AmericanoRoundStatus.CANCELLED)
                .flatMap(m -> Stream.of(m.getTeam1Id(), m.getTeam2Id())).filter(Objects::nonNull).allMatch(seen::add);
    }

    private boolean courtConflict(State state, AmericanoMatch other, List<Long> affected) {
        AmericanoMatch first = state.source();
        if (first.isInProgress() && other.isInProgress() && first.getCourtNumber() != null
                && first.getCourtNumber().equals(other.getCourtNumber())) return true;
        return state.all().stream().filter(m -> !isSelected(m, first, other) && m.isActive())
                .anyMatch(m -> containsTeam(affected, m.getTeam1Id()) || containsTeam(affected, m.getTeam2Id())
                        || m.isInProgress() && m.getTeam1Id() != null && m.getTeam2Id() != null && m.getCourtNumber() != null
                        && (first.isInProgress() && m.getCourtNumber().equals(first.getCourtNumber())
                        || other.isInProgress() && m.getCourtNumber().equals(other.getCourtNumber())));
    }

    private static boolean containsTeam(List<Long> affected, Long id) {
        return id != null && affected.contains(id);
    }

    private static boolean includes(AmericanoMatch match, Long id) {
        return id.equals(match.getTeam1Id()) || id.equals(match.getTeam2Id());
    }

    private boolean validLinks(State state, AmericanoMatch other) {
        if (!openDestination(state, state.source()) || !openDestination(state, other)) return false;
        Set<String> occupied = new HashSet<>();
        return state.all().stream().filter(m -> targets(m, state.source()) || targets(m, other))
                .allMatch(m -> {
                    AmericanoMatch target = targets(m, state.source()) ? state.source() : other;
                    Integer slot = m.getNextMatchSlot();
                    return m.isCompleted() && m.getPlayoffStage() != null
                            && m.getPlayoffStage().ordinal() + 1 == target.getPlayoffStage().ordinal()
                            && slot != null && (slot == 1 || slot == 2)
                            && winner(m) != null && winner(m).equals(idAt(target, slot))
                            && occupied.add(target.getId() + ":" + slot);
                });
    }

    private boolean openDestination(State state, AmericanoMatch source) {
        if (source.getNextMatchId() == null) return true;
        Integer slot = source.getNextMatchSlot();
        if (slot == null || (slot != 1 && slot != 2)) return false;
        return state.all().stream().filter(m -> targets(source, m))
                .anyMatch(m -> m.getPlayoffStage() != null
                        && m.getPlayoffStage().ordinal() == source.getPlayoffStage().ordinal() + 1
                        && idAt(m, slot) == null && (MESSAGE + "incomplete").equals(block(m)));
    }

    private List<AmericanoMatch> moveLinks(State state, int slot, AmericanoMatch other, int otherSlot) {
        AmericanoMatch first = state.source();
        List<AmericanoMatch> moved = new ArrayList<>();
        for (AmericanoMatch m : state.all()) {
            if (targets(m, first) && Objects.equals(m.getNextMatchSlot(), slot)) {
                m.setNextMatchId(other.getId());
                m.setNextMatchSlot(otherSlot);
                moved.add(m);
            } else if (targets(m, other) && Objects.equals(m.getNextMatchSlot(), otherSlot)) {
                m.setNextMatchId(first.getId());
                m.setNextMatchSlot(slot);
                moved.add(m);
            }
        }
        return moved;
    }

    private void updatePair(State state, AmericanoMatch match, Pair pair) {
        AmericanoTeam first = state.teams().get(pair.first().id());
        AmericanoTeam second = state.teams().get(pair.second().id());
        match.setTeam1Id(first.getId());
        match.setTeam1Player1(first.getPlayer1());
        match.setTeam1Player2(first.getPlayer2());
        match.setTeam2Id(second.getId());
        match.setTeam2Player1(second.getPlayer1());
        match.setTeam2Player2(second.getPlayer2());
        match.setNote(first.getDisplayName() + " vs " + second.getDisplayName());
        match.setPlayoffPairChanged(true);
    }

    private void notifyUpdated(AmericanoMatch... changed) {
        var payloads = Stream.of(changed).map(playoff::toMatchDto).toList();
        AfterCommit.run(() -> payloads.forEach(dto -> webSocket.notifyTeamPlayoffMatchUpdated(dto.getTournamentId(), dto)));
    }

    private static String block(AmericanoMatch match) {
        return block(match, false);
    }

    private static String block(AmericanoMatch match, boolean correction) {
        if (!correction && hasResult(match)) return MESSAGE + "completed";
        if (!correction && Boolean.TRUE.equals(match.getPlayoffPlayStarted())) return MESSAGE + "started";
        if (match.getStatus() == AmericanoRoundStatus.CANCELLED) return MESSAGE + "cancelled";
        if (match.getTeam1Id() == null || match.getTeam2Id() == null) return MESSAGE + "incomplete";
        return null;
    }

    private static boolean hasResult(AmericanoMatch match) {
        return match.isCompleted() || match.getCompletedAt() != null || match.getTeam1Games() != null
                || match.getTeam2Games() != null || match.getTeam1Score() != null || match.getTeam2Score() != null;
    }

    static boolean scoredOrStarted(AmericanoMatch match) {
        return hasResult(match) || Boolean.TRUE.equals(match.getPlayoffPlayStarted());
    }

    private static boolean needsLegacyConfirmation(AmericanoMatch match) {
        return !scoredOrStarted(match) && match.getPlayoffPlayStarted() == null;
    }

    private static boolean isSelected(AmericanoMatch m, AmericanoMatch first, AmericanoMatch second) {
        return m.getId().equals(first.getId()) || m.getId().equals(second.getId());
    }

    private static boolean targets(AmericanoMatch m, AmericanoMatch target) {
        return target.getId().equals(m.getNextMatchId());
    }

    private static boolean sameTeams(AmericanoMatch m, Pair pair) {
        return Objects.equals(m.getTeam1Id(), pair.first().id()) && Objects.equals(m.getTeam2Id(), pair.second().id())
                || Objects.equals(m.getTeam1Id(), pair.second().id()) && Objects.equals(m.getTeam2Id(), pair.first().id());
    }

    private static Long winner(AmericanoMatch m) {
        if (m.getTeam1Games() == null || m.getTeam2Games() == null) return null;
        return m.getTeam1Games() > m.getTeam2Games() ? m.getTeam1Id() : m.getTeam2Id();
    }

    private static Long loser(AmericanoMatch m) {
        Long winner = winner(m);
        if (winner == null) return null;
        return winner.equals(m.getTeam1Id()) ? m.getTeam2Id() : m.getTeam1Id();
    }

    private static Long idAt(AmericanoMatch m, int slot) {
        return slot == 1 ? m.getTeam1Id() : m.getTeam2Id();
    }

    private static Team teamAt(Pair pair, int slot) {
        return slot == 1 ? pair.first() : pair.second();
    }

    private static Pair replace(Pair pair, int slot, Team team) {
        return new Pair(pair.matchId(), pair.matchNumber(), pair.court(), slot == 1 ? team : pair.first(),
                slot == 2 ? team : pair.second(), null, null);
    }

    private static void validateSlot(int slot) {
        if (slot != 1 && slot != 2) throw invalid("other_match");
    }

    private static InvalidStateException invalid(String code) {
        return new InvalidStateException(MESSAGE + code);
    }

    private record State(AmericanoMatch source, List<AmericanoMatch> all, Map<Long, AmericanoTeam> teams) {
        Team team(Long id) {
            if (id == null) return null;
            AmericanoTeam team = teams.get(id);
            return team == null ? new Team(id, null, "—") : new Team(id, team.getTeamNumber(), team.getDisplayName());
        }

        Pair pair(AmericanoMatch m) {
            return new Pair(m.getId(), m.getMatchNumber(), m.getCourtNumber(), team(m.getTeam1Id()),
                    team(m.getTeam2Id()), m.getTeam1Games(), m.getTeam2Games());
        }
    }
}
