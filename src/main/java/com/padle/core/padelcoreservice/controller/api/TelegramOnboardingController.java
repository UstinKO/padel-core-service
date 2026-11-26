package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.service.AccountService;
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

    public static final String SESSION_TOKEN = "telegramOnboardingToken";

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
    public PlayerTelegramOnboarding.Review review(HttpSession session, Authentication auth) {
        return onboarding.review(token(session),accounts.player(auth));
    }

    @PostMapping("/api/players/me/telegram/onboarding")
    @PreAuthorize("hasRole('PLAYER')")
    public PlayerTelegramOnboarding.Review confirm(HttpSession session, Authentication auth, Locale locale) {
        return onboarding.stage(token(session),accounts.player(auth),locale);
    }

    public static String token(HttpSession session) {
        return session.getAttribute(SESSION_TOKEN) instanceof String value ? value : "";
    }
}
