package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        model.addAttribute("isGuestAccount", PlayerPadel.isGuestEmail(player.getEmail()));

        return "admin/players/details";
    }

    // Выдача/перевыпуск временного пароля гостевого аккаунта (issue #217).
    // Кнопка на странице и так видна только SUPER_ADMIN + гостевому email,
    // но бэкенд обязан перепроверить оба условия самостоятельно.
    @PostMapping("/{id}/guest-credentials")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateGuestCredentials(@PathVariable Long id,
                                                                          @AuthenticationPrincipal Owner owner) {
        Map<String, Object> result = new HashMap<>();

        if (!owner.isSuperAdmin()) {
            result.put("success", false);
            result.put("message", msg("admin.players.details.error.no_permission_level"));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
        }

        try {
            String rawPassword = playerService.generarCredencialesInvitado(id);
            PlayerResponseDto player = playerService.obtenerJugadorPorId(id);
            result.put("success", true);
            result.put("email", player.getEmail());
            result.put("password", rawPassword);
            return ResponseEntity.ok(result);
        } catch (IllegalStateException e) {
            result.put("success", false);
            result.put("message", msg("admin.players.details.error.not_guest_account"));
            return ResponseEntity.badRequest().body(result);
        } catch (IllegalArgumentException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            log.error("Error generando credenciales de invitado para jugador {}: {}", id, e.getMessage());
            result.put("success", false);
            result.put("message", msg("admin.players.details.error.guest_credentials_failed"));
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PostMapping("/{id}/nivel")
    public Object updatePlayerNivel(@PathVariable Long id,
                                     @RequestParam(required = false) String nivel,
                                     @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
                                     @AuthenticationPrincipal Owner owner,
                                     RedirectAttributes redirectAttributes) {
        // Список игроков (LFPT-218) шлёт этот же эндпоинт через fetch с заголовком
        // X-Requested-With — автосейв без перезагрузки страницы (заказчик проставляет
        // уровни сотням игроков подряд, редирект каждый раз сбрасывал прокрутку).
        // Страница деталей игрока продолжает обычный form-submit с редиректом.
        boolean ajax = "XMLHttpRequest".equals(requestedWith);

        if (!owner.isSuperAdmin()) {
            String message = msg("admin.players.details.error.no_permission_level");
            if (ajax) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("success", false, "message", message));
            }
            redirectAttributes.addFlashAttribute("errorMessage", message);
            return "redirect:/admin/players/" + id;
        }

        log.info("Actualizando nivel del jugador {} a: {}", id, nivel);

        try {
            Nivel nivelParsed = (nivel != null && !nivel.isBlank()) ? Nivel.valueOf(nivel) : null;
            if (nivelParsed != null && !Nivel.isPlayerLevel(nivelParsed)) {
                throw new IllegalArgumentException("Nivel no aplicable a un jugador: " + nivel);
            }
            playerService.actualizarNivelJugador(id, nivelParsed);
            if (ajax) {
                return ResponseEntity.ok(Map.of("success", true));
            }
            redirectAttributes.addFlashAttribute("successMessage",
                    msg("admin.players.details.success.level_updated"));
        } catch (IllegalArgumentException e) {
            log.error("Error actualizando nivel del jugador {}: {}", id, e.getMessage());
            String message = msg("admin.players.details.error.invalid_level");
            if (ajax) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
            }
            redirectAttributes.addFlashAttribute("errorMessage", message);
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