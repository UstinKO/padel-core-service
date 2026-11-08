package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.americano.PlayoffPairExchange;
import com.padle.core.padelcoreservice.service.conduct.TournamentConductHistory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tournaments/team-playoff/api/matches/{matchId}")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
@RequiredArgsConstructor
public class TeamPlayoffPairController {
    private final TournamentAccessService access;
    private final PlayoffPairExchange pairs;
    private final TournamentConductHistory history;
    private final MessageSource messages;

    public record Preview(long version, PlayoffPairExchange.Options options) {}
    public record Saved(boolean success) {}
    public record Problem(boolean success, String message) {}
    public record LegacyCandidate(Long matchId, int slot, Long teamId, String teamDisplayName) {}

    @GetMapping("/pair-options")
    public Preview options(@PathVariable Long matchId, @RequestParam int slot,
                           @RequestParam(defaultValue = "false") boolean correction, @AuthenticationPrincipal Owner owner) {
        Long tournamentId = access.assertCanManageAmericanoMatch(owner, matchId);
        long before = history.revision(tournamentId);
        var options = pairs.options(matchId, slot, correction);
        if (before != history.revision(tournamentId)) throw new InvalidStateException("conduct.history.stale");
        return new Preview(before, options);
    }

    @GetMapping("/swap-candidates")
    public List<LegacyCandidate> legacyOptions(@PathVariable Long matchId, @AuthenticationPrincipal Owner owner) {
        access.assertCanManageAmericanoMatch(owner, matchId);
        return pairs.options(matchId, 1, false).candidates().stream().filter(option -> option.block() == null)
                .map(option -> new LegacyCandidate(option.matchId(), option.slot(), option.team().id(), option.team().name()))
                .toList();
    }

    @PostMapping("/correct-pair")
    public Saved correct(@PathVariable Long matchId, @RequestParam Long expectedConductVersion,
                         @RequestBody PlayoffPairExchange.Correction correction, @AuthenticationPrincipal Owner owner) {
        access.assertCanManageAmericanoMatch(owner, matchId);
        requireVersion(expectedConductVersion);
        pairs.correct(matchId, correction);
        return new Saved(true);
    }

    @PostMapping("/swap-team")
    public Saved exchange(@PathVariable Long matchId, @RequestParam(required = false) Long expectedConductVersion,
                          @RequestBody PlayoffPairExchange.Request request, @AuthenticationPrincipal Owner owner) {
        access.assertCanManageAmericanoMatch(owner, matchId);
        requireVersion(expectedConductVersion);
        pairs.exchange(matchId, request);
        return new Saved(true);
    }

    @PostMapping("/play-start")
    public Saved start(@PathVariable Long matchId, @RequestParam Long expectedConductVersion,
                       @AuthenticationPrincipal Owner owner) {
        access.assertCanManageAmericanoMatch(owner, matchId);
        requireVersion(expectedConductVersion);
        pairs.markStarted(matchId);
        return new Saved(true);
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<Problem> conflict(InvalidStateException exception) {
        String key = exception.getMessage();
        return ResponseEntity.status(409).body(new Problem(false,
                messages.getMessage(key, null, key, LocaleContextHolder.getLocale())));
    }

    private void requireVersion(Long version) {
        if (version == null || version < 0) throw new InvalidStateException("admin.playoff.swap.refresh");
    }
}
