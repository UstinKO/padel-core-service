package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.AccountService;
import com.padle.core.padelcoreservice.service.TelegramParticipationCoordinator;
import com.padle.core.padelcoreservice.telegram.*;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PlayerTelegramController {
    private final AccountService accounts;
    private final PlayerTelegramLinks links;
    private final PlayerTelegramStore store;
    private final TelegramParticipationCoordinator participation;
    private final PlayerTelegramNotifications notifications;
    private final PlayerTelegramSummary summary;

    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @GetMapping("/players/me/telegram")
    @PreAuthorize("hasRole('PLAYER')")
    public LinkStatus status(Authentication auth) {
        return links.status(accounts.player(auth));
    }

    @PostMapping("/players/me/telegram/link")
    @PreAuthorize("hasRole('PLAYER')")
    public Link link(Authentication auth, Locale locale) {
        return links.issue(accounts.player(auth), locale);
    }

    @PutMapping("/players/me/telegram/preference")
    @PreAuthorize("hasRole('PLAYER')")
    public LinkStatus preference(Authentication auth, @Valid @RequestBody PreferenceChange change) {
        return links.preference(accounts.player(auth), change.preference());
    }

    @PutMapping("/players/me/telegram/invitation")
    @PreAuthorize("hasRole('PLAYER')")
    public LinkStatus invitation(Authentication auth, @Valid @RequestBody InvitationChange change) {
        return links.invitation(accounts.player(auth), change.dismissed());
    }

    @GetMapping("/admin/telegram/connections")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ConnectionStatistics connections() {
        return store.connectionStatistics();
    }

    @DeleteMapping("/players/me/telegram")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLAYER')")
    public void disconnect(Authentication auth) {
        store.disconnect(accounts.player(auth).getId());
    }

    @GetMapping("/admin/tournaments/{id}/telegram")
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN','ORGANIZER','ADMIN','CLUB_ADMIN')")
    public Summary overview(@PathVariable long id, @AuthenticationPrincipal Owner owner) {
        return participation.summary(id, owner);
    }

    @PostMapping("/admin/tournaments/{id}/telegram/confirmations")
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN','ORGANIZER','ADMIN','CLUB_ADMIN')")
    public Summary request(@PathVariable long id, @AuthenticationPrincipal Owner owner) {
        if (!links.available()) {
            throw new AccountException(HttpStatus.CONFLICT, "TELEGRAM_UNAVAILABLE");
        }
        participation.request(id, owner).forEach(registration -> notifications.send(registration, false));
        return participation.summary(id, owner);
    }

    @PostMapping("/admin/telegram/summary")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void summary(@AuthenticationPrincipal Owner owner) {
        summary.manual(owner);
    }
}
