package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.service.DoubleTournamentRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PartnerRegistrationController {

    private final DoubleTournamentRegistrationService doubleTournamentRegistrationService;

    @GetMapping("/double-registration/complete")
    public String completePartnerRegistration(@RequestParam String token,
                                              RedirectAttributes redirectAttributes) {
        try {
            doubleTournamentRegistrationService.confirmPartnerRegistration(token);
            redirectAttributes.addFlashAttribute("successMessage",
                    "¡Registro completado! Por favor confirma tu email para activar tu cuenta.");
        } catch (Exception e) {
            log.error("Error completing partner registration: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al completar el registro: " + e.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/double-registration/accept-pair")
    public String acceptPairPage(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        return "accept-pair";
    }
}