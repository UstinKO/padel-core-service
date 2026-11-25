package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.ParticipationDtos.*;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.AccountService;
import com.padle.core.padelcoreservice.service.TournamentParticipationService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ParticipationController {
    private final TournamentParticipationService participation;
    private final AccountService accounts;

    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @GetMapping("/players/me/participation")
    @PreAuthorize("hasRole('PLAYER')")
    public List<RequestSummary> requests(Authentication auth) {
        return participation.list(accounts.player(auth));
    }

    @GetMapping("/players/me/participation/{id}")
    @PreAuthorize("hasRole('PLAYER')")
    public Review review(@PathVariable UUID id, Authentication auth) {
        return participation.review(id, accounts.player(auth), false);
    }

    @PostMapping("/players/me/participation/{id}")
    @PreAuthorize("hasRole('PLAYER')")
    public Review answer(@PathVariable UUID id, @Valid @RequestBody Answer answer, Authentication auth) {
        return participation.answer(id, accounts.player(auth), answer.decision(), false);
    }

    @PutMapping("/players/me/tournaments/{id}/replacement-search")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLAYER')")
    public void replacementSearch(@PathVariable long id, @Valid @RequestBody ReplacementSearch search, Authentication auth) {
        participation.setReplacementSearch(id, accounts.player(auth), search.enabled());
    }

    @GetMapping("/admin/tournaments/{id}/participation")
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN','ORGANIZER','ADMIN','CLUB_ADMIN')")
    public List<OrganizerStatus> tournamentStatus(@PathVariable long id, @AuthenticationPrincipal Owner owner) {
        return participation.organizerStatus(id, owner);
    }
}
