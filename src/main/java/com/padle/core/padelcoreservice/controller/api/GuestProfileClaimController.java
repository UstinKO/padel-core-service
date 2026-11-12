package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.GuestProfileClaimDtos.*;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.AccountService;
import com.padle.core.padelcoreservice.service.GuestProfileClaimService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GuestProfileClaimController {
    private final GuestProfileClaimService claims;
    private final AccountService accounts;

    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @PostMapping("/players/me/guest-profile-claims")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PLAYER')")
    public RequestReceipt request(Authentication auth, @Valid @RequestBody Request request) {
        return claims.request(accounts.player(auth), request);
    }

    @GetMapping("/admin/guest-profile-claims/{reference}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public Review review(@PathVariable UUID reference) {
        return claims.review(reference);
    }

    @PostMapping("/admin/guest-profile-claims/{reference}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public Result approve(@PathVariable UUID reference, @Valid @RequestBody Approval approval,
                          @AuthenticationPrincipal Owner organizer) {
        return claims.approve(reference, approval, organizer.getId());
    }

    @PostMapping("/admin/players/{guestId}/activate-guest-profile")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public Result activate(@PathVariable long guestId, @Valid @RequestBody Activation activation,
                           @AuthenticationPrincipal Owner organizer) {
        return claims.activate(guestId, activation, organizer.getId());
    }
}
