package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.PlayerEvents.ContactVisibility;
import com.padle.core.padelcoreservice.dto.PlayerEvents.Overview;
import com.padle.core.padelcoreservice.dto.PlayerEvents.Withdrawal;
import com.padle.core.padelcoreservice.service.AccountService;
import com.padle.core.padelcoreservice.service.PlayerEventsService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/players/me/events")
@PreAuthorize("hasRole('PLAYER')")
@RequiredArgsConstructor
public class PlayerEventsController {
    private final AccountService accounts;
    private final PlayerEventsService events;

    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @GetMapping
    public Overview overview(Authentication authentication) {
        return events.overview(accounts.player(authentication));
    }

    @PostMapping("/{registrationId}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable long registrationId, @Valid @RequestBody Withdrawal input,
                         Authentication authentication) {
        events.withdraw(accounts.player(authentication), registrationId, input.version());
    }

    @PutMapping("/{registrationId}/contact-visibility")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void visibility(@PathVariable long registrationId, @Valid @RequestBody ContactVisibility input,
                           Authentication authentication) {
        events.changeContactVisibility(accounts.player(authentication), registrationId, input);
    }
}
