package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final TournamentService tournamentService;

    @GetMapping("/confirm")
    public String confirmFromWaitlist(@RequestParam Long registrationId, Model model) {
        log.info("Confirming from waitlist with registrationId: {}", registrationId);

        try {
            boolean confirmed = tournamentService.confirmFromWaitlist(registrationId);
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