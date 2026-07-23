package com.padle.core.padelcoreservice.controller.player;

import com.padle.core.padelcoreservice.dto.PasswordResetConfirm;
import com.padle.core.padelcoreservice.dto.PasswordResetRequest;
import com.padle.core.padelcoreservice.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/recuperar-password")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/solicitar")
    @ResponseBody
    public Response solicitarReset(@Valid @RequestBody PasswordResetRequest request) {
        log.info("Solicitud de reset para email: {}", request.getEmail());

        boolean enviado = passwordResetService.requestPasswordReset(request.getEmail());

        if (enviado) {
            return new Response(true, "Si el email existe, recibirás instrucciones para recuperar tu contraseña");
        } else {
            return new Response(false, "Error al procesar la solicitud");
        }
    }

    /**
     * Этот метод обрабатывает GET запросы на /recuperar-password?token=...
     */
    @GetMapping
    public String mostrarFormularioNuevoPassword(@RequestParam String token, Model model) {
        log.info("Mostrando formulario para nuevo password con token: {}", token);

        boolean tokenValido = passwordResetService.validateToken(token);

        if (!tokenValido) {
            model.addAttribute("error", "El enlace de recuperación ha expirado o ya ha sido utilizado");
            return "error";
        }

        // Перенаправляем на главную с токеном в URL, JS подхватит и покажет модальное окно
        return "redirect:/?token=" + token;
    }

    @PostMapping("/confirmar")
    @ResponseBody
    public Response confirmarReset(@Valid @RequestBody PasswordResetConfirm confirmDto) {
        log.info("Confirmando reset de password");

        boolean exito = passwordResetService.resetPassword(confirmDto);

        if (exito) {
            return new Response(true, "Contraseña actualizada correctamente");
        } else {
            return new Response(false, "Error al actualizar la contraseña. El enlace puede haber expirado");
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class Response {
        private boolean success;
        private String message;
    }
}