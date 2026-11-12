package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.ParticipationDtos.*;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.ParticipationRequest;
import com.padle.core.padelcoreservice.model.ParticipationRequest.Trigger;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.ParticipationStatus;
import com.padle.core.padelcoreservice.repository.ParticipationRequestRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TournamentParticipationService {
    private final ParticipationRequestRepository requests;
    private final TournamentRepository tournaments;
    private final TournamentRegistrationRepository registrations;
    private final TournamentService tournamentService;
    private final PairTermsService pairTerms;
    private final TournamentAccessService access;
    private final ParticipationRequests participationRequests;

    public List<RequestSummary> list(PlayerPadel actor) {
        return requests.findCurrentForActor(actor.getId()).stream()
                .filter(request -> ParticipationRequests.canActFor(request, actor.getId()))
                .filter(request -> ParticipationRequests.sameCycle(request, request.getRegistration()))
                .map(request -> ParticipationRequests.summary(request, actor.getId()))
                .toList();
    }

    public List<OrganizerStatus> organizerStatus(long tournamentId, Owner owner) {
        access.assertCanManageTournament(owner, tournamentId);
        var rows = registrations.findByTournamentId(tournamentId);
        var current = participationRequests.current(rows);
        return rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsActive()) || current.containsKey(row.getId()))
                .map(row -> {
                    var request = current.get(row.getId());
                    return new OrganizerStatus(row.getId(), row.getPlayer().getId(), row.getPlayer().getNombreCompleto(),
                            request == null ? ParticipationStatus.NOT_REQUIRED : request.getStatus(),
                            row.needsReplacementInMain());
                }).toList();
    }

    public Review review(UUID id, PlayerPadel actor, boolean fromEmail) {
        var requested = findForActor(id, actor);
        return review(requested, actor, fromEmail);
    }

    @Transactional
    public Review answer(UUID id, PlayerPadel actor, Decision decision, boolean fromEmail) {
        long tournamentId = requests.findTournamentId(id).orElseThrow(TournamentParticipationService::notFound);
        tournaments.lockById(tournamentId).orElseThrow(TournamentParticipationService::notFound);
        var request = findForActor(id, actor);
        var current = review(request, actor, fromEmail);
        var desired = decision == Decision.CONFIRM ? ParticipationStatus.CONFIRMED : ParticipationStatus.DECLINED;
        if (current.outdated() || request.getStatus() == desired) return current;
        if (decision == Decision.CONFIRM ? !current.canRespond() : !current.canDecline()) {
            throw new AccountException(HttpStatus.CONFLICT, "PARTICIPATION_RESPONSE_UNAVAILABLE");
        }
        request.respond(desired, actor.getId(), OffsetDateTime.now());
        if (decision == Decision.DECLINE) {
            tournamentService.declineParticipation(request.getRegistration(), actor.getId());
        }
        return review(request, actor, fromEmail);
    }

    @Transactional
    public void setReplacementSearch(long tournamentId, PlayerPadel actor, boolean enabled) {
        tournaments.lockById(tournamentId).orElseThrow(TournamentParticipationService::notFound);
        var row = registrations.findByTournamentIdAndPlayerId(tournamentId, actor.getId())
                .filter(TournamentRegistration::needsReplacementInMain)
                .filter(r -> pairTerms.isCancellable(r.getTournament()))
                .orElseThrow(TournamentParticipationService::notFound);
        row.setReplacementSearch(enabled);
    }

    private ParticipationRequest findForActor(UUID id, PlayerPadel actor) {
        return requests.findById(id)
                .filter(request -> ParticipationRequests.canActFor(request, actor.getId()))
                .orElseThrow(TournamentParticipationService::notFound);
    }

    private Review review(ParticipationRequest requested, PlayerPadel actor, boolean fromEmail) {
        var row = requested.getRegistration();
        var tournament = row.getTournament();
        boolean outdated = !ParticipationRequests.sameCycle(requested, row);
        var current = outdated ? requests.findByRegistrationIdAndSupersededAtIsNull(row.getId())
                .filter(request -> ParticipationRequests.sameCycle(request, row))
                .filter(request -> ParticipationRequests.canActFor(request, actor.getId()))
                .orElse(null) : requested;
        boolean expired = fromEmail && !requested.getExpiresAt().isAfter(OffsetDateTime.now());
        var status = current == null ? ParticipationStatus.NOT_REQUIRED : current.getStatus();
        boolean open = !outdated && !expired && Boolean.TRUE.equals(row.getIsActive())
                && row.isConfirmed() && pairTerms.isCancellable(tournament);
        boolean canRespond = open && status == ParticipationStatus.PENDING;
        boolean canDecline = open && (status == ParticipationStatus.PENDING || status == ParticipationStatus.CONFIRMED)
                && (requested.getTrigger() == Trigger.RESERVE
                    || PairTermsService.accepted(row) || tournament.canCancelRegistration());
        return new Review(requested.getId(), tournament.getId(), tournament.getNombre(),
                row.getPlayer().getNombreCompleto(), status, !row.getPlayer().getId().equals(actor.getId()),
                canRespond, canDecline, expired, outdated);
    }

    private static AccountException notFound() {
        return new AccountException(HttpStatus.NOT_FOUND, "PARTICIPATION_REQUEST_NOT_FOUND");
    }
}
