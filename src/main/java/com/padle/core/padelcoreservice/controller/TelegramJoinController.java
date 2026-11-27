package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.security.AccountDestination;
import com.padle.core.padelcoreservice.service.AccountService;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramLinks;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramOnboarding;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

@Controller
@RequiredArgsConstructor
public class TelegramJoinController {
    private static final String PATH = "/telegram/join";
    private final AccountService accounts;
    private final PlayerTelegramLinks links;
    private final PlayerTelegramOnboarding onboarding;
    private final MessageSource messages;

    public record Join(String state, String playerName) {}

    @ModelAttribute
    public void privateResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    @GetMapping(PATH)
    public String page(Authentication auth, Model model, RedirectAttributes redirect, Locale locale) {
        if (onboarding.available() && (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken)) {
            redirect.addAttribute(AccountDestination.PARAMETER, PATH);
            return "redirect:/login";
        }
        model.addAttribute("join", review(auth));
        if (new ClassPathResource("templates/telegram-join.html").exists()) {
            return "telegram-join";
        }
        model.addAttribute("type", "warning");
        model.addAttribute("message", messages.getMessage("account.TELEGRAM_UNAVAILABLE", null, locale));
        return "waitlist-confirmation";
    }

    @PostMapping(PATH)
    @PreAuthorize("hasRole('PLAYER')")
    public RedirectView connect(Authentication auth, Locale locale, RedirectAttributes flash) {
        if (!onboarding.available()) return redirect(PATH);
        try {
            var player = accounts.player(auth);
            var status = links.status(player);
            if (!status.available() || status.connected()) return redirect(PATH);
            return redirect(links.issue(player, locale).url());
        } catch (AccountException exception) {
            flash.addFlashAttribute("joinFailed", true);
            return redirect(PATH);
        }
    }

    private Join review(Authentication auth) {
        if (!onboarding.available()) return new Join("UNAVAILABLE", "");
        try {
            var player = accounts.player(auth);
            var status = links.status(player);
            if (!status.available()) return new Join("UNAVAILABLE", "");
            return new Join(status.connected() ? "CONNECTED" : "READY", player.getNombreCompleto());
        } catch (AccountException exception) {
            return new Join("ACCOUNT_REQUIRED", "");
        }
    }

    private static RedirectView redirect(String target) {
        var view = new RedirectView(target);
        view.setStatusCode(HttpStatus.SEE_OTHER);
        view.setExposeModelAttributes(false);
        return view;
    }
}
