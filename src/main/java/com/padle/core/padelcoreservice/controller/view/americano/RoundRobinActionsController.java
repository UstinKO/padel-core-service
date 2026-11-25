package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.dto.americano.RoundRobinAdditionalMatchRequest;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.americano.RoundRobinAdditionalMatches;
import com.padle.core.padelcoreservice.service.americano.TeamAmericanoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Locale;

@RestController
@RequestMapping("/tournaments/team-americano/api/round-robin")
@RequiredArgsConstructor
public class RoundRobinActionsController {
    private final TournamentAccessService access;
    private final RoundRobinAdditionalMatches additional;
    private final TeamAmericanoService service;
    private final MessageSource messages;

    public record Saved(Long id, boolean success) {}
    public record Score(@NotNull @Min(0) Integer team1Score, @NotNull @Min(0) Integer team2Score) {}

    @PostMapping("/{id}/additional")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    public Saved add(@PathVariable Long id, @RequestBody @Valid RoundRobinAdditionalMatchRequest request,
                     @AuthenticationPrincipal Owner owner) {
        access.assertCanManageTournament(owner, id);
        return new Saved(additional.add(id, request).getId(), true);
    }

    @PostMapping("/matches/{id}/result")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    public Saved score(@PathVariable Long id, @RequestBody @Valid Score score,
                       @AuthenticationPrincipal Owner owner) {
        access.assertCanManageAmericanoMatch(owner, id);
        return new Saved(service.submitMatchResult(id, score.team1Score(), score.team2Score()).getId(), true);
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ProblemDetail> invalid(InvalidStateException error, Locale locale) {
        var detail = messages.getMessage(error.getMessage(), null,
                messages.getMessage("round_robin.error.invalid", null, locale), locale);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> missing(ResourceNotFoundException error, Locale locale) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                messages.getMessage("round_robin.error.missing", null, locale)));
    }
}
