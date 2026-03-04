package com.padle.core.padelcoreservice.controller.view;

import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.service.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ConfirmacionEmailController {

    private final PlayerService playerService;

    @GetMapping("/players/confirmar-email")
    public String confirmarEmail(@RequestParam("codigo") String codigo, RedirectAttributes redirectAttributes) {
        log.info("🔐 Procesando confirmación de email con código: {}", codigo);

        try {
            PlayerResponseDto jugador = playerService.confirmarEmail(codigo);
            log.info("✅ Email confirmado exitosamente para: {}", jugador.getEmail());

            redirectAttributes.addFlashAttribute("successMessage",
                    "¡Email confirmado exitosamente! Ya puedes iniciar sesión.");

            return "redirect:/login?confirmed=true";

        } catch (IllegalArgumentException e) {
            log.error("❌ Error al confirmar email: {}", e.getMessage());

            redirectAttributes.addFlashAttribute("errorMessage",
                    "El código de confirmación es inválido o ya ha expirado.");

            return "redirect:/login?error=confirm";
        }
    }
}