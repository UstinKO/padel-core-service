package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/players")
@RequiredArgsConstructor
@Slf4j
public class AdminPlayerController {

    private final PlayerService playerService;
    private final TournamentService tournamentService;
    private final MessageSource messageSource;

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    @GetMapping
    public String listPlayers(Model model, @AuthenticationPrincipal Owner owner) {
        log.info("Listando todos los jugadores para administrador");

        List<PlayerResponseDto> players = playerService.getAllPlayers();
        model.addAttribute("players", players);
        model.addAttribute("totalPlayers", players.size());
        model.addAttribute("isSuperAdmin", owner.isSuperAdmin());
        model.addAttribute("playersWithRegistrations", playerService.getPlayerIdsWithRegistrations());

        return "admin/players/list";
    }

    @GetMapping("/{id}")
    public String viewPlayer(@PathVariable Long id, Model model, @AuthenticationPrincipal Owner owner) {
        PlayerResponseDto player = playerService.obtenerJugadorPorId(id);
        List<TournamentRegistrationDto> registrations = tournamentService.getActiveRegistrationsByPlayer(id);

        model.addAttribute("player", player);
        model.addAttribute("registrations", registrations);
        model.addAttribute("isSuperAdmin", owner.isSuperAdmin());
        model.addAttribute("hasRegistrations", playerService.hasTournamentRegistrations(id));

        return "admin/players/details";
    }

    @PostMapping("/{id}/nivel")
    public String updatePlayerNivel(@PathVariable Long id,
                                     @RequestParam(required = false) String nivel,
                                     @AuthenticationPrincipal Owner owner,
                                     RedirectAttributes redirectAttributes) {
        if (!owner.isSuperAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    msg("admin.players.details.error.no_permission_level"));
            return "redirect:/admin/players/" + id;
        }

        log.info("Actualizando nivel del jugador {} a: {}", id, nivel);

        try {
            Nivel nivelParsed = (nivel != null && !nivel.isBlank()) ? Nivel.valueOf(nivel) : null;
            if (nivelParsed != null && !Nivel.isPlayerLevel(nivelParsed)) {
                throw new IllegalArgumentException("Nivel no aplicable a un jugador: " + nivel);
            }
            playerService.actualizarNivelJugador(id, nivelParsed);
            redirectAttributes.addFlashAttribute("successMessage",
                    msg("admin.players.details.success.level_updated"));
        } catch (IllegalArgumentException e) {
            log.error("Error actualizando nivel del jugador {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    msg("admin.players.details.error.invalid_level"));
        }

        return "redirect:/admin/players/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deletePlayer(@PathVariable Long id,
                                @AuthenticationPrincipal Owner owner,
                                RedirectAttributes redirectAttributes) {
        if (!owner.isSuperAdmin()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    msg("admin.players.error.no_permission_delete"));
            return "redirect:/admin/players/" + id;
        }

        log.info("Eliminando jugador: {}", id);

        try {
            playerService.eliminarJugador(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    msg("admin.players.success.deleted"));
            return "redirect:/admin/players";
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/players/" + id;
        } catch (Exception e) {
            log.error("Error eliminando al jugador {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    msg("admin.players.error.delete_generic"));
            return "redirect:/admin/players/" + id;
        }
    }
}