package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.application.tournament.TournamentType;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.service.WebSocketService;
import com.padle.core.padelcoreservice.service.conduct.ConductChange;
import com.padle.core.padelcoreservice.service.conduct.TournamentConductHistory;
import com.padle.core.padelcoreservice.util.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamPlayoffCourts {
    private final AmericanoMatchRepository matches;
    private final TeamPlayoffService playoff;
    private final TournamentConductHistory history;
    private final WebSocketService webSocket;

    public record Court(int number, List<AmericanoMatchDto> occupants) {}
    public record Options(long version, AmericanoMatchDto match, boolean canQueue, List<Court> courts) {}
    public record Change(long version, Integer courtNumber, Long swapMatchId, boolean confirmed) {}

    public Options options(Long matchId) {
        var source = requireMatch(matchId);
        long tournamentId = source.getTournament().getId();
        long version = history.revision(tournamentId);
        var active = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(tournamentId).stream()
                .filter(AmericanoMatch::isInProgress).toList();
        var courts = IntStream.rangeClosed(1, playoff.getConfiguredCourts(tournamentId))
                .mapToObj(number -> new Court(number, active.stream()
                        .filter(match -> Objects.equals(number, match.getCourtNumber()))
                        .map(playoff::toMatchDto).toList())).toList();
        if (history.revision(tournamentId) != version) throw invalid("stale");
        return new Options(version, playoff.toMatchDto(source), canQueue(source), courts);
    }

    @Transactional
    @ConductChange(value = ConductChange.Action.COURT, subject = ConductChange.Subject.AMERICANO_MATCH)
    public AmericanoMatch change(Long matchId, Change change) {
        var source = requireMatch(matchId);
        long tournamentId = source.getTournament().getId();
        if (history.revision(tournamentId) != change.version()) throw invalid("stale");
        if (Objects.equals(source.getCourtNumber(), change.courtNumber()) && change.swapMatchId() == null) return source;
        if ((source.isInProgress() || source.isCompleted() || change.swapMatchId() != null) && !change.confirmed()) {
            throw invalid("confirm");
        }
        if (change.courtNumber() == null) {
            if (!canQueue(source) || change.swapMatchId() != null) throw invalid("queue_unavailable");
            source.setCourtNumber(null);
            source.setStatus(AmericanoRoundStatus.QUEUED);
            source.setStartedAt(null);
        } else {
            if (!source.isCompleted() && (source.getTeam1Id() == null || source.getTeam2Id() == null)) {
                throw invalid("unavailable");
            }
            validateCourt(tournamentId, change.courtNumber());
            changeAssignedCourt(source, change);
        }
        matches.save(source);
        notifyUpdated(source);
        return source;
    }

    private void changeAssignedCourt(AmericanoMatch source, Change change) {
        if (source.isCompleted()) {
            if (change.swapMatchId() != null) throw invalid("swap_unavailable");
        } else {
            var occupants = occupants(source, change.courtNumber());
            if (change.swapMatchId() == null) {
                if (!occupants.isEmpty()) throw invalid("occupied");
            } else {
                swap(source, change, occupants);
            }
        }
        source.setCourtNumber(change.courtNumber());
        if (source.isQueued()) {
            source.setStatus(AmericanoRoundStatus.IN_PROGRESS);
            // Назначение корта сохраняет возможность отдельно отметить фактическое начало.
            source.setPlayoffPlayStarted(false);
        }
    }

    private void swap(AmericanoMatch source, Change change, List<AmericanoMatch> occupants) {
        if (!source.isInProgress() || occupants.size() != 1
                || !occupants.get(0).getId().equals(change.swapMatchId())) throw invalid("swap_unavailable");
        int previous = Objects.requireNonNullElse(source.getCourtNumber(), 0);
        validateCourt(source.getTournament().getId(), previous);
        var other = occupants.get(0);
        if (occupants(other, previous).stream().anyMatch(match -> !match.getId().equals(source.getId()))) {
            throw invalid("occupied");
        }
        other.setCourtNumber(previous);
        matches.save(other);
        notifyUpdated(other);
    }

    private List<AmericanoMatch> occupants(AmericanoMatch source, int court) {
        return matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(source.getTournament().getId()).stream()
                .filter(AmericanoMatch::isInProgress)
                .filter(match -> !match.getId().equals(source.getId()) && Objects.equals(match.getCourtNumber(), court))
                .toList();
    }

    private void validateCourt(long tournamentId, int court) {
        if (court < 1 || court > playoff.getConfiguredCourts(tournamentId)) throw invalid("invalid");
    }

    private AmericanoMatch requireMatch(long id) {
        var match = matches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Match not found"));
        if (match.getTournament().getTipo() != TournamentType.AMERICANO_TEAMS
                || match.getStatus() == AmericanoRoundStatus.CANCELLED) throw invalid("unavailable");
        return match;
    }

    private boolean canQueue(AmericanoMatch match) {
        return !match.isCompleted() && match.getCompletedAt() == null
                && match.getTeam1Id() != null && match.getTeam2Id() != null
                && !Boolean.TRUE.equals(match.getPlayoffPlayStarted())
                && (match.getPlayoffStage() != null || match.getStartedAt() == null
                    || Boolean.FALSE.equals(match.getPlayoffPlayStarted()))
                && match.getTeam1Games() == null && match.getTeam2Games() == null
                && match.getTeam1Score() == null && match.getTeam2Score() == null;
    }

    private void notifyUpdated(AmericanoMatch match) {
        var dto = playoff.toMatchDto(match);
        AfterCommit.run(() -> webSocket.notifyTeamPlayoffMatchUpdated(dto.getTournamentId(), dto));
    }

    private InvalidStateException invalid(String reason) {
        return new InvalidStateException("admin.playoff.courts." + reason);
    }
}
