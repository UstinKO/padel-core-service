package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.service.DoubleTournamentRegistrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.stereotype.Controller;

import java.util.Locale;

// LFPT-0668: self-service действия из email для резервной заявки без партнёра — без логина,
// по токену (аналогично /waitlist/confirm). Переиспользует общий шаблон waitlist-confirmation.
@Controller
@RequestMapping("/reserva/accion")
@RequiredArgsConstructor
@Slf4j
public class ReserveActionController {

    private final DoubleTournamentRegistrationService doubleTournamentRegistrationService;
    private final MessageSource messageSource;

    @GetMapping("/confirmar")
    public String confirmarActualidad(@RequestParam String token, Model model, Locale locale) {
        try {
            doubleTournamentRegistrationService.confirmReserveStillActive(token);
            model.addAttribute("type", "success");
            model.addAttribute("message", messageSource.getMessage("reserve.action.confirm.success", null, locale));
        } catch (TournamentRegistrationException e) {
            log.info("Reserve confirm-still-active link not eligible: {}", e.getMessage());
            model.addAttribute("type", "error");
            model.addAttribute("message", messageSource.getMessage("reserve.action.link_invalid", null, locale));
        }
        return "waitlist-confirmation";
    }

    @GetMapping("/cancelar")
    public String cancelarPagina(@RequestParam String token, Model model, Locale locale) {
        try {
            model.addAttribute("preview", doubleTournamentRegistrationService.previewReserveCancellation(token));
            model.addAttribute("token", token);
            return "reserve-cancel-confirm";
        } catch (TournamentRegistrationException e) {
            log.info("Reserve cancel link not eligible: {}", e.getMessage());
            model.addAttribute("type", "error");
            model.addAttribute("message", messageSource.getMessage("reserve.action.link_invalid", null, locale));
            return "waitlist-confirmation";
        }
    }

    @PostMapping("/cancelar")
    public String cancelar(@RequestParam String token, RedirectAttributes redirectAttributes, Locale locale) {
        try {
            doubleTournamentRegistrationService.cancelReserveByToken(token);
            redirectAttributes.addFlashAttribute("type", "success");
            redirectAttributes.addFlashAttribute("message",
                    messageSource.getMessage("reserve.action.cancel.success", null, locale));
        } catch (TournamentRegistrationException e) {
            log.info("Reserve cancel failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("type", "error");
            redirectAttributes.addFlashAttribute("message", messageSource.getMessage("reserve.action.link_invalid", null, locale));
        }
        return "redirect:/reserva/accion/resultado";
    }

    @GetMapping("/resultado")
    public String resultado() {
        return "waitlist-confirmation";
    }
}
