package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.conduct.AdditionalPointsService;
import com.padle.core.padelcoreservice.service.conduct.AdditionalPointsService.Change;
import com.padle.core.padelcoreservice.service.conduct.AdditionalPointsService.Editor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping({"/admin/tournaments/{tournamentId}/additional-points",
        "/admin/tournaments/{tournamentId}/{mode:finished-results}/additional-points"})
@RequiredArgsConstructor
public class AdditionalPointsController {

    private final AdditionalPointsService points;
    private final TournamentAccessService access;

    @GetMapping
    public Editor editor(@PathVariable Long tournamentId, @PathVariable(required = false) String mode,
                         @AuthenticationPrincipal Owner owner) {
        return points.editor(tournamentId, mode != null, owner);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@PathVariable Long tournamentId, @PathVariable(required = false) String mode,
                    @RequestHeader("Idempotency-Key") UUID requestKey, @RequestParam long expectedConductVersion,
                    @RequestParam long playerId, @RequestParam int roundNumber, @RequestParam int delta,
                    @RequestParam(defaultValue = "") String note, @AuthenticationPrincipal Owner owner) {
        access.assertCanManageTournament(owner, tournamentId);
        points.add(tournamentId, mode != null, new Change(playerId, roundNumber, delta, note), owner);
    }

    @PostMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void edit(@PathVariable Long tournamentId, @PathVariable(required = false) String mode, @PathVariable UUID id,
                     @RequestHeader("Idempotency-Key") UUID requestKey, @RequestParam long expectedConductVersion,
                     @RequestParam long playerId, @RequestParam int roundNumber, @RequestParam int delta,
                     @RequestParam(defaultValue = "") String note, @AuthenticationPrincipal Owner owner) {
        access.assertCanManageTournament(owner, tournamentId);
        points.edit(tournamentId, mode != null, id, new Change(playerId, roundNumber, delta, note), owner);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long tournamentId, @PathVariable(required = false) String mode, @PathVariable UUID id,
                       @RequestHeader("Idempotency-Key") UUID requestKey, @RequestParam long expectedConductVersion,
                       @AuthenticationPrincipal Owner owner) {
        access.assertCanManageTournament(owner, tournamentId);
        points.cancel(tournamentId, mode != null, id, owner);
    }
}
