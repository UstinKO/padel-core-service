package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.service.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/players")
@RequiredArgsConstructor
@Slf4j
public class AdminPlayerController {

    private final PlayerService playerService;

    @GetMapping
    public String listPlayers(Model model) {
        log.info("Listando todos los jugadores para administrador");

        List<PlayerResponseDto> players = playerService.getAllPlayers();
        model.addAttribute("players", players);
        model.addAttribute("totalPlayers", players.size());

        return "admin/players/list";
    }

    @GetMapping("/{id}")
    public String viewPlayer(@PathVariable Long id, Model model) {
        log.info("Viendo detalles del jugador: {}", id);

        PlayerResponseDto player = playerService.obtenerJugadorPorId(id);
        model.addAttribute("player", player);

        return "admin/players/details";
    }

    @PostMapping("/{id}/toggle-status")
    public String togglePlayerStatus(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.info("Cambiando estado del jugador: {}", id);

        try {
            PlayerResponseDto player = playerService.obtenerJugadorPorId(id);
            if (player.isActivo()) {
                playerService.desactivarJugador(id);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Jugador desactivado correctamente");
            } else {
                playerService.activarJugador(id);
                redirectAttributes.addFlashAttribute("successMessage",
                        "Jugador activado correctamente");
            }
        } catch (Exception e) {
            log.error("Error cambiando estado del jugador: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al cambiar el estado del jugador");
        }

        return "redirect:/admin/players/" + id;
    }
}