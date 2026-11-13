package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.ParticipationDtos.Decision;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.service.AccountService;
import com.padle.core.padelcoreservice.service.TournamentParticipationService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/participacion")
@RequiredArgsConstructor
public class ParticipationLinkController {
    private final TournamentParticipationService participation;
    private final AccountService accounts;
    private final MessageSource messages;

    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    @GetMapping("/{id}")
    public String view(@PathVariable UUID id, Authentication auth, Model model, RedirectAttributes redirect) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            redirect.addAttribute("destination", "/participacion/" + id);
            return "redirect:/login";
        }
        try {
            model.addAttribute("request", participation.review(id, accounts.player(auth), true));
            // Ссылка остаётся безопасной при отдельном выпуске серверной части.
            if (new ClassPathResource("templates/participation-response.html").exists()) {
                return "participation-response";
            }
            model.addAttribute("message", message("participation.cabinet"));
        } catch (AccountException exception) {
            model.addAttribute("message", message("account.PARTICIPATION_REQUEST_NOT_FOUND"));
        }
        model.addAttribute("type", "warning");
        return "waitlist-confirmation";
    }

    @PostMapping("/{id}")
    public String answer(@PathVariable UUID id, @RequestParam Decision decision,
                         Authentication auth, RedirectAttributes redirect) {
        try {
            participation.answer(id, accounts.player(auth), decision, true);
        } catch (AccountException exception) {
            redirect.addFlashAttribute("responseError", message("account." + exception.getMessage()));
        }
        return "redirect:/participacion/" + id;
    }

    private String message(String key) {
        return messages.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}
