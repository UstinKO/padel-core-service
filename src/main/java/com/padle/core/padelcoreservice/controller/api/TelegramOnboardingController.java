package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.service.AccountService;
import com.padle.core.padelcoreservice.security.TelegramOnboardingSession;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramOnboarding;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Locale;

@RestController
@RequiredArgsConstructor
public class TelegramOnboardingController {
    private final PlayerTelegramOnboarding onboarding;
    private final AccountService accounts;

    @ModelAttribute
    public void privateResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        response.setHeader("Referrer-Policy","no-referrer");
    }

    @GetMapping("/api/admin/telegram/invitation")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ORGANIZER','OWNER','CLUB_ADMIN','ADMIN')")
    public PlayerTelegramOnboarding.Invitation invitation() { return onboarding.invitation(); }

    @GetMapping("/api/players/me/telegram/onboarding")
    @PreAuthorize("hasRole('PLAYER')")
    public PlayerTelegramOnboarding.Review review(@RequestParam(defaultValue="") String request, HttpSession session, Authentication auth) {
        return onboarding.review(TelegramOnboardingSession.token(session,request),accounts.player(auth));
    }

    @PostMapping("/api/players/me/telegram/onboarding")
    @PreAuthorize("hasRole('PLAYER')")
    public PlayerTelegramOnboarding.Review confirm(@RequestParam(defaultValue="") String request, HttpSession session, Authentication auth, Locale locale) {
        return onboarding.stage(TelegramOnboardingSession.token(session,request),accounts.player(auth),locale);
    }
}
