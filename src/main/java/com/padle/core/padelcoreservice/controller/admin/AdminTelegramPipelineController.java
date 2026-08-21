package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.TelegramPipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/telegram-pipeline")
@RequiredArgsConstructor
@Slf4j
public class AdminTelegramPipelineController {

    private final TelegramPipelineService telegramPipelineService;

    @GetMapping
    public String show(Model model, @AuthenticationPrincipal Owner owner) {
        if (!owner.isSuperAdmin()) {
            return "redirect:/admin";
        }
        model.addAttribute("paused", telegramPipelineService.isPaused());
        return "admin/telegram-pipeline";
    }

    @PostMapping("/toggle")
    public String toggle(@AuthenticationPrincipal Owner owner, RedirectAttributes redirectAttributes) {
        if (!owner.isSuperAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "No tienes permiso para cambiar el estado del pipeline");
            return "redirect:/admin";
        }
        log.info("Toggling Telegram pipeline paused state, initiated by {}", owner.getEmail());
        boolean paused = telegramPipelineService.togglePaused(owner.getEmail());
        redirectAttributes.addFlashAttribute("successMessage",
                paused ? "Pipeline de Telegram pausado" : "Pipeline de Telegram reanudado");
        return "redirect:/admin/telegram-pipeline";
    }
}
