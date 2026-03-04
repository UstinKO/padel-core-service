package com.padle.core.padelcoreservice.controller.player;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/players")
@RequiredArgsConstructor
@Slf4j
public class PlayerDashboardController {

    private final TournamentService tournamentService;
    private final ObjectMapper objectMapper;

    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal PlayerPadel player) {
        log.info("Accessing dashboard for player: {}", player != null ? player.getEmail() : "null");

        // Если player null, значит пользователь не аутентифицирован как игрок
        if (player == null) {
            log.warn("Player is null, redirecting to login");
            return "redirect:/login";
        }

        try {
            // Получаем турниры, открытые для регистрации
            List<TournamentDto> upcomingTournaments = tournamentService.getVisibleTournamentsForPlayer();

            // Получаем ID турниров, на которые игрок уже зарегистрирован
            List<Long> myTournamentIds = tournamentService.getActiveRegistrationsByPlayer(player.getId())
                    .stream()
                    .map(TournamentRegistrationDto::getTournamentId)
                    .collect(Collectors.toList());

            // Получаем статистику игрока (временно заглушки)
            model.addAttribute("player", player);
            model.addAttribute("torneosInscritos", myTournamentIds.size());
            model.addAttribute("torneosGanados", 0);
            model.addAttribute("rankingPosition", 42);
            model.addAttribute("puntosRanking", 1250);

            // Передаем данные турниров в JSON для JavaScript
            try {
                String tournamentsJson = objectMapper.writeValueAsString(upcomingTournaments);
                String myTournamentIdsJson = objectMapper.writeValueAsString(myTournamentIds);
                model.addAttribute("tournamentsJson", tournamentsJson);
                model.addAttribute("myTournamentIdsJson", myTournamentIdsJson);
            } catch (Exception e) {
                log.error("Error converting to JSON", e);
                model.addAttribute("tournamentsJson", "[]");
                model.addAttribute("myTournamentIdsJson", "[]");
            }

            return "players/dashboard";

        } catch (Exception e) {
            log.error("Error loading dashboard: {}", e.getMessage(), e);
            return "redirect:/?error";
        }
    }

    @PostMapping("/tournaments/{tournamentId}/register")
    @ResponseBody
    public ResponseEntity<?> registerForTournament(@PathVariable Long tournamentId,
                                                   @AuthenticationPrincipal PlayerPadel player) {
        log.info("Player {} registering for tournament {}", player.getId(), tournamentId);

        try {
            TournamentRegistrationDto registration = tournamentService.registerPlayer(tournamentId, player.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", registration.getStatus() == RegistrationStatus.CONFIRMED ?
                    "¡Registro confirmado!" : "Agregado a la lista de espera");
            response.put("status", registration.getStatus());
            response.put("position", registration.getPosition());
            response.put("waitlistPosition", registration.getWaitlistPosition());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error registering for tournament", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/tournaments/{tournamentId}/cancel")
    @ResponseBody
    public ResponseEntity<?> cancelRegistration(@PathVariable Long tournamentId,
                                                @AuthenticationPrincipal PlayerPadel player,
                                                @RequestParam(required = false) String reason) {
        log.info("Player {} cancelling registration for tournament {}", player.getId(), tournamentId);

        try {
            tournamentService.cancelRegistration(tournamentId, player.getId(), reason);

            // Проверим, действительно ли отмена произошла
            Optional<TournamentRegistrationDto> check = tournamentService.getRegistration(tournamentId, player.getId());
            log.info("After cancellation, registration exists: {}", check.isPresent());
            if (check.isPresent()) {
                log.info("Registration status: {}, active: {}",
                        check.get().getStatus(), check.get());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Inscripción cancelada exitosamente");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error cancelling registration", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}