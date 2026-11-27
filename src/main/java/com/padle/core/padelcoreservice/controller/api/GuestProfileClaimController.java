package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.application.identity.GuestProfileClaims.ProfileChoice;
import com.padle.core.application.identity.GuestProfileClaims.Status;
import com.padle.core.padelcoreservice.dto.GuestProfileClaimDtos.*;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.AccountService;
import com.padle.core.padelcoreservice.service.GuestProfileClaimService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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

    @GetMapping("/players/me/guest-profile-claims")
    @PreAuthorize("hasRole('PLAYER')")
    public List<RequestReceipt> ownRequests(Authentication auth) {
        return claims.ownRequests(accounts.player(auth));
    }

    @GetMapping("/admin/guest-profile-claims")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public Queue queue(@RequestParam(defaultValue = "") @Size(max = 150) String search,
                       @RequestParam(required = false) Status status,
                       @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page) {
        return claims.queue(search, status, page);
    }

    @PostMapping("/admin/guest-profile-claims/{reference}/decision")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public Review decide(@PathVariable UUID reference, @Valid @RequestBody ReviewDecision decision,
                         @AuthenticationPrincipal Owner organizer) {
        return claims.decide(reference, decision, organizer.getId());
    }

    @GetMapping("/admin/guest-profile-claims/profiles")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public List<ProfileChoice> profileChoices(@RequestParam @Size(max = 150) String search,
                                              @RequestParam boolean guest) {
        return claims.profileChoices(search, guest);
    }

    @GetMapping("/admin/guest-profile-claims/manual")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public Review manualReview(@RequestParam long guestId, @RequestParam long accountId) {
        return claims.manualReview(guestId, accountId);
    }

    @PostMapping("/admin/guest-profile-claims/manual")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public Result manualMerge(@Valid @RequestBody ManualMerge request, @AuthenticationPrincipal Owner organizer) {
        return claims.manualMerge(request, organizer.getId());
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
