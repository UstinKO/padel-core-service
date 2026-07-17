package com.padle.core.padelcoreservice.controller.player;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
            // ДОБАВЛЕНО: поле для Telegram ника
            @RequestParam(required = false) String telegramUsername,
            @RequestParam(required = false) String nivelJugador,
            @RequestParam(required = false) String preferredLocale,
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
                String newPhone = telefono.isBlank() ? null : telefono.trim();
                if (newPhone != null && playerService.existsByTelefono(newPhone)
                        && !newPhone.equals(player.getTelefono())) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Este número de teléfono ya está en uso por otro jugador");
                    return "redirect:/perfil";
                }
                player.setTelefono(newPhone);
                actualizado = true;
            }

            // Nivel de juego
            Nivel nivelParsed = (nivelJugador != null && !nivelJugador.isBlank())
                    ? Nivel.valueOf(nivelJugador) : null;
            if (nivelParsed != player.getNivelJugador()) {
                player.setNivelJugador(nivelParsed);
                actualizado = true;
            }

            // Idioma preferido
            if (preferredLocale != null && !preferredLocale.isBlank()
                    && java.util.Set.of("es", "ru", "en").contains(preferredLocale)
                    && !preferredLocale.equals(player.getPreferredLocale())) {
                player.setPreferredLocale(preferredLocale);
                actualizado = true;
            }

            // ДОБАВЛЕНО: обновляем Telegram ник
            if (telegramUsername != null && !telegramUsername.equals(player.getTelegramUsername())) {
                if (!telegramUsername.isBlank()) {
                    String tg = telegramUsername.trim();
                    // Добавляем @ если не указан
                    if (!tg.startsWith("@")) {
                        tg = "@" + tg;
                    }
                    if (!tg.equals(player.getTelegramUsername())
                            && playerService.existsByTelegramUsername(tg)) {
                        redirectAttributes.addFlashAttribute("errorMessage",
                                "Este usuario de Telegram ya está en uso por otro jugador");
                        return "redirect:/perfil";
                    }
                    player.setTelegramUsername(tg);
                } else {
                    player.setTelegramUsername(null);
                }
                actualizado = true;
            }

            if (newPassword != null && !newPassword.isEmpty()) {
                if (currentPassword == null || currentPassword.isEmpty()) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Debes ingresar tu contraseña actual para cambiarla");
                    return "redirect:/perfil";
                }

                if (!playerService.checkPassword(player, currentPassword)) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "La contraseña actual es incorrecta");
                    return "redirect:/perfil";
                }

                if (!newPassword.equals(confirmPassword)) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "Las contraseñas nuevas no coinciden");
                    return "redirect:/perfil";
                }

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

        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate phone/contact on perfil update: {}", e.getMessage());
            boolean isTelegramConflict = e.getMostSpecificCause().getMessage() != null
                    && e.getMostSpecificCause().getMessage().contains("idx_player_telegram_username_unique");
            redirectAttributes.addFlashAttribute("errorMessage", isTelegramConflict
                    ? "Este usuario de Telegram ya está en uso por otro jugador"
                    : "Este número de teléfono ya está en uso por otro jugador");
        } catch (Exception e) {
            log.error("Error actualizando perfil: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al actualizar el perfil");
        }

        return "redirect:/perfil";
    }
}