package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffCourts;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tournaments/team-playoff/api/matches/{matchId}/court-assignment")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
@RequiredArgsConstructor
public class TeamPlayoffCourtController {
    private final TournamentAccessService access;
    private final TeamPlayoffCourts courts;
    private final MessageSource messages;

    public record Saved(boolean success) {}
    public record Problem(boolean success, String message) {}

    @GetMapping
    public TeamPlayoffCourts.Options options(@PathVariable Long matchId, @AuthenticationPrincipal Owner owner) {
        access.assertCanManageAmericanoMatch(owner, matchId);
        return courts.options(matchId);
    }

    @PostMapping
    public Saved change(@PathVariable Long matchId, @RequestBody TeamPlayoffCourts.Change change,
                        @AuthenticationPrincipal Owner owner) {
        access.assertCanManageAmericanoMatch(owner, matchId);
        courts.change(matchId, change);
        return new Saved(true);
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<Problem> conflict(InvalidStateException exception) {
        String message = messages.getMessage(exception.getMessage(), null, exception.getMessage(),
                LocaleContextHolder.getLocale());
        return ResponseEntity.status(409).body(new Problem(false, message));
    }
}
