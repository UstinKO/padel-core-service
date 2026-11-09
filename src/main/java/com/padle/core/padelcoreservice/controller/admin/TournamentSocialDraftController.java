package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.TournamentSocialDraft;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.TournamentSocialDraftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TournamentSocialDraftController {

    private final TournamentSocialDraftService service;

    @GetMapping("/admin/tournaments/{id}/social-draft")
    @PreAuthorize("hasAnyRole('OWNER', 'SUPER_ADMIN', 'ORGANIZER', 'ADMIN', 'CLUB_ADMIN')")
    public ResponseEntity<TournamentSocialDraft> prepare(@PathVariable Long id, @AuthenticationPrincipal Owner owner) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.prepare(owner, id));
    }
}
