package com.padle.core.padelcoreservice.controller.player;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.util.SecurityUtils;
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
    public String verPerfil(Model model, @AuthenticationPrincipal Object principal) {
        PlayerPadel player = SecurityUtils.extractPlayer(principal);

        if (player == null) {
            log.warn("Intento de acceso a perfil sin autenticación");
            return "redirect:/login";
        }

        log.info("Viendo perfil de jugador: {}", player.getEmail());

        model.addAttribute("player", player);
        return "players/perfil";
    }

    @PostMapping("/actualizar")
    public String actualizarPerfil(
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String apellido,
            @RequestParam(required = false) String telefono,
            @RequestParam(required = false) String currentPassword,
            @RequestParam(required = false) String newPassword,
            @RequestParam(required = false) String confirmPassword,
            RedirectAttributes redirectAttributes) {

        PlayerPadel player = SecurityUtils.extractPlayer(principal);

        if (player == null) {
            log.warn("Intento de actualizar perfil sin autenticación");
            return "redirect:/login";
        }

        log.info("Actualizando perfil de jugador: {}", player.getEmail());

        try {
            // Actualizar datos básicos
            boolean actualizado = false;

            if (nombre != null && !nombre.isEmpty() && !nombre.equals(player.getNombre())) {
                player.setNombre(nombre);
                actualizado = true;
            }

            if (apellido != null && !apellido.isEmpty() && !apellido.equals(player.getApellido())) {
                player.setApellido(apellido);
                actualizado = true;
            }

            if (telefono != null && !telefono.equals(player.getTelefono())) {
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
            } else {
                redirectAttributes.addFlashAttribute("infoMessage",
                        "No se realizaron cambios en el perfil");
            }

        } catch (Exception e) {
            log.error("Error actualizando perfil: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al actualizar el perfil: " + e.getMessage());
        }

        return "redirect:/perfil";
    }
}