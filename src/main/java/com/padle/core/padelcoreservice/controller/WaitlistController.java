package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Подтверждение приглашения из листа ожидания — двухшаговый флоу (issue #194):
 * GET только показывает страницу с деталями (безопасно для пред-фетча ссылок
 * email-сканерами), реальное подтверждение — только по явному POST с той же кнопки.
 */
@Slf4j
@Controller
@RequestMapping("/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final TournamentService tournamentService;

    @GetMapping("/confirm")
    public String showConfirmationPage(@RequestParam String token, Model model) {
        try {
            TournamentService.WaitlistInvitationPreview preview =
                    tournamentService.getWaitlistInvitationPreview(token);
            model.addAttribute("type", "pending");
            model.addAttribute("preview", preview);
        } catch (InvalidStateException | ResourceNotFoundException e) {
            log.warn("Waitlist invitation preview unavailable: {}", e.getMessage());
            model.addAttribute("message", e.getMessage());
            model.addAttribute("type", "warning");
        }
        return "waitlist-confirmation";
    }

    @PostMapping("/confirm")
    public String confirmFromWaitlist(@RequestParam String token, Model model) {
        log.info("Confirming from waitlist with token");

        try {
            boolean confirmed = tournamentService.confirmFromWaitlist(token);
            if (confirmed) {
                model.addAttribute("message", "¡Felicidades! Tu participación ha sido confirmada.");
                model.addAttribute("type", "success");
            }
        } catch (InvalidStateException e) {
            log.warn("Invalid state: {}", e.getMessage());
            model.addAttribute("message", e.getMessage());
            model.addAttribute("type", "warning");
        } catch (Exception e) {
            log.error("Error confirming from waitlist", e);
            model.addAttribute("message", "Ha ocurrido un error. Por favor, intenta de nuevo.");
            model.addAttribute("type", "error");
        }

        return "waitlist-confirmation";
    }
}
