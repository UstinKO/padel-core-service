package com.padle.core.padelcoreservice.controller.player;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.service.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/perfil")
@RequiredArgsConstructor
@Slf4j
public class PerfilController {

    private final PlayerService playerService;

    @GetMapping
    public String verPerfil(Model model, @AuthenticationPrincipal PlayerPadel player) {
        log.info("Viendo perfil de jugador: {}", player.getEmail());

        model.addAttribute("player", player);
        return "players/perfil";
    }

    @PostMapping("/actualizar")
    public String actualizarPerfil(
            @AuthenticationPrincipal PlayerPadel player,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String apellido,
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false) String currentPassword,
            @RequestParam(required = false) String newPassword,
            @RequestParam(required = false) String confirmPassword,
            RedirectAttributes redirectAttributes) {

        log.info("Actualizando perfil de jugador: {}", player.getEmail());

        try {
            // Actualizar datos básicos
            boolean actualizado = false;

            if (nombre != null && !nombre.isEmpty()) {
                player.setNombre(nombre);
                actualizado = true;
            }

            if (apellido != null && !apellido.isEmpty()) {
                player.setApellido(apellido);
                actualizado = true;
            }

            if (telefono != null) {
                player.setTelefono(telefono);
                actualizado = true;
            }

            // Cambiar contraseña si se solicitó
            if (newPassword != null && !newPassword.isEmpty()) {
                if (currentPassword == null || currentPassword.isEmpty()) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Debes ingresar tu contraseña actual para cambiarla");
                    return "redirect:/perfil";
                }

                // Verificar contraseña actual
                if (!playerService.checkPassword(player, currentPassword)) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "La contraseña actual es incorrecta");
                    return "redirect:/perfil";
                }

                // Verificar que las contraseñas nuevas coincidan
                if (!newPassword.equals(confirmPassword)) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Las contraseñas nuevas no coinciden");
                    return "redirect:/perfil";
                }

                // Cambiar contraseña
                playerService.cambiarPassword(player.getId(), newPassword);
                actualizado = true;
                log.info("Contraseña actualizada para jugador: {}", player.getEmail());
            }

            if (actualizado) {
                playerService.actualizarJugador(player);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Perfil actualizado correctamente");
            }

        } catch (Exception e) {
            log.error("Error actualizando perfil: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al actualizar el perfil: " + e.getMessage());
        }

        return "redirect:/perfil";
    }
}