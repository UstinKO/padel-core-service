package com.padle.core.padelcoreservice.controller.player;

import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.security.oauth2.CustomOAuth2User;
import com.padle.core.padelcoreservice.service.DoubleTournamentRegistrationService;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/players")
@RequiredArgsConstructor
@Slf4j
public class PlayerDashboardController {

    private final TournamentService tournamentService;
    private final ObjectMapper objectMapper;
    private final DoubleTournamentRegistrationService doubleTournamentRegistrationService;
    private final PlayerRepository playerRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal Object principal) {
        log.info("Accessing dashboard with principal type: {}",
                principal != null ? principal.getClass().getName() : "null");

        // Извлекаем PlayerPadel из principal
        PlayerPadel player = extractPlayerFromPrincipal(principal);

        if (player == null) {
            // Если principal — это Owner (SUPER_ADMIN или ORGANIZER),
            // редиректим на админ-панель, а не на /login
            if (principal instanceof com.padle.core.padelcoreservice.model.Owner) {
                log.info("Principal is Owner, redirecting to admin panel");
                return "redirect:/admin";
            }
            log.warn("Player is null, redirecting to login");
            return "redirect:/login";
        }

        log.info("Dashboard accessed by player: {} (ID: {})", player.getEmail(), player.getId());

        try {
            // Получаем турниры, открытые для регистрации
            List<TournamentDto> upcomingTournaments = tournamentService.getVisibleTournamentsForPlayer();

            // Турниры, в которых игрок уже участвовал, и id турниров с активной регистрацией —
            // из одного запроса регистраций (раньше запрашивались дважды, см. #178)
            TournamentService.PlayerDashboardTournaments dashboardTournaments =
                    tournamentService.getHistoricalAndActiveTournamentsByPlayer(player.getId());
            List<TournamentDto> historicalTournaments = dashboardTournaments.historical();
            List<Long> myTournamentIds = dashboardTournaments.activeTournamentIds();

            // Получаем статистику игрока (временно заглушки)
            model.addAttribute("player", player);
            model.addAttribute("torneosInscritos", myTournamentIds.size());
            model.addAttribute("torneosGanados", 0);
            model.addAttribute("rankingPosition", 42);
            model.addAttribute("puntosRanking", 1250);

            // Передаем данные турниров в JSON для JavaScript
            try {
                String tournamentsJson = objectMapper.writeValueAsString(upcomingTournaments);
                String historicalJson = objectMapper.writeValueAsString(historicalTournaments);
                String myTournamentIdsJson = objectMapper.writeValueAsString(myTournamentIds);
                model.addAttribute("tournamentsJson", tournamentsJson);
                model.addAttribute("historicalJson", historicalJson);
                model.addAttribute("myTournamentIdsJson", myTournamentIdsJson);
            } catch (Exception e) {
                log.error("Error converting to JSON", e);
                model.addAttribute("tournamentsJson", "[]");
                model.addAttribute("historicalJson", "[]");
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
                                                   @AuthenticationPrincipal Object principal) {
        PlayerPadel player = extractPlayerFromPrincipal(principal);

        if (player == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Usuario no autenticado"
            ));
        }

        log.info("Player {} registering for tournament {}", player.getId(), tournamentId);

        // ПРОВЕРКА КОНТАКТОВ - возвращаем needContact, а не ошибку
        if (!player.hasValidContact()) {
            log.warn("Player {} has no valid contact (phone or telegram)", player.getId());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Para inscribirte en un torneo necesitas tener al menos un dato de contacto: WhatsApp o Telegram",
                    "needContact", true
            ));
        }

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
                                                @AuthenticationPrincipal Object principal,
                                                @RequestParam(required = false) String reason) {
        PlayerPadel player = extractPlayerFromPrincipal(principal);

        if (player == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Usuario no autenticado"));
        }

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

    /**
     * Вспомогательный метод для извлечения PlayerPadel из principal
     */
    private PlayerPadel extractPlayerFromPrincipal(Object principal) {
        if (principal == null) {
            return null;
        }

        if (principal instanceof CustomOAuth2User) {
            return ((CustomOAuth2User) principal).getPlayer();
        } else if (principal instanceof PlayerPadel) {
            return (PlayerPadel) principal;
        } else if (principal instanceof UserDetails) {
            String email = ((UserDetails) principal).getUsername();
            log.info("Principal is UserDetails: {}", email);
            return playerRepository.findByEmail(email).orElse(null); // ← загружаем из БД
        }

        log.warn("Unknown principal type: {}", principal.getClass().getName());
        return null;
    }

    @PostMapping("/tournaments/{tournamentId}/cancel-double")
    @ResponseBody
    public ResponseEntity<?> cancelDoubleRegistration(@PathVariable Long tournamentId,
                                                      @AuthenticationPrincipal Object principal,
                                                      @RequestParam String option,
                                                      @RequestParam(required = false) String reason,
                                                      @RequestParam(required = false) String replaceData) {
        PlayerPadel player = extractPlayerFromPrincipal(principal);

        if (player == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Usuario no autenticado"));
        }

        log.info("Player {} cancelling double registration for tournament {} with option: {}",
                player.getId(), tournamentId, option);

        try {
            Map<String, Object> response = new HashMap<>();

            // Получаем информацию о регистрации
            TournamentRegistrationDto registration = tournamentService.getRegistration(tournamentId, player.getId())
                    .orElseThrow(() -> new TournamentRegistrationException("Registro no encontrado"));

            // Проверяем, зарегистрирован ли партнер
            boolean isPartnerRegistered = registration.getPartnerId() != null;

            if ("self".equals(option)) {
                // Отмена только себя - доступно только если партнер зарегистрирован
                if (!isPartnerRegistered) {
                    return ResponseEntity.badRequest().body(Map.of("success", false,
                            "message", "No puedes cancelar solo tu inscripción porque tu compañero no está registrado en el sistema. Debes cancelar toda la pareja."));
                }

                tournamentService.cancelRegistration(tournamentId, player.getId(), reason);
                response.put("success", true);
                response.put("message", "Tu inscripción ha sido cancelada. Tu compañero sigue inscrito.");

            } else if ("full".equals(option)) {
                // Отмена всей пары - доступно всегда
                tournamentService.cancelPairRegistration(tournamentId, player.getId(), reason);
                response.put("success", true);
                response.put("message", "La inscripción de toda la pareja ha sido cancelada.");

            } else if ("replace".equals(option)) {
                // Замена себя другим игроком - доступно только если партнер зарегистрирован
                if (!isPartnerRegistered) {
                    return ResponseEntity.badRequest().body(Map.of("success", false,
                            "message", "No puedes reemplazarte porque tu compañero no está registrado en el sistema. Debes cancelar toda la pareja."));
                }

                if (replaceData == null || replaceData.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("success", false,
                            "message", "Datos del nuevo jugador no proporcionados"));
                }

                // Парсим данные нового игрока
                ObjectMapper mapper = new ObjectMapper();
                Map<String, String> newPlayerData = mapper.readValue(replaceData, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>(){});

                // Создаем PartnerRegistrationDto из полученных данных
                PartnerRegistrationDto partnerDto = new PartnerRegistrationDto();
                partnerDto.setNombre(newPlayerData.get("firstName"));
                partnerDto.setApellido(newPlayerData.get("lastName"));
                partnerDto.setTelefono(newPlayerData.get("phone"));
                partnerDto.setEmail(newPlayerData.get("email"));

                // Получаем DTO турнира
                TournamentDto tournamentDto = tournamentService.getTournamentDtoById(tournamentId)
                        .orElseThrow(() -> new RuntimeException("Torneo no encontrado"));

                // Вызываем метод замены
                doubleTournamentRegistrationService.replacePlayerInPair(tournamentDto, player.getId(), partnerDto, reason);

                response.put("success", true);
                response.put("message", "Has sido reemplazado exitosamente. El nuevo jugador recibirá una invitación.");

            } else {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "message", "Opción no válida"));
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error cancelling double registration", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/tournaments/{tournamentId}/my-registration")
    @ResponseBody
    public ResponseEntity<?> getMyRegistration(@PathVariable Long tournamentId,
                                               @AuthenticationPrincipal Object principal) {
        PlayerPadel player = extractPlayerFromPrincipal(principal);

        if (player == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Usuario no autenticado"));
        }

        Optional<TournamentRegistrationDto> registration = tournamentService.getRegistration(tournamentId, player.getId());

        if (registration.isEmpty()) {
            return ResponseEntity.ok(Map.of("registered", false));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("registered", true);
        response.put("partnerId", registration.get().getPartnerId());
        response.put("partnerFirstName", registration.get().getPartnerNombre());
        response.put("partnerLastName", registration.get().getPartnerApellido());
        response.put("hasRegisteredPartner", registration.get().getPartnerId() != null);

        return ResponseEntity.ok(response);
    }

    // Добавьте эти методы в PlayerDashboardController.java

    @PatchMapping("/api/players/me/contact")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateContact(
            @AuthenticationPrincipal Object principal,
            @RequestBody Map<String, String> contactData) {

        PlayerPadel player = extractPlayerFromPrincipal(principal);

        if (player == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Usuario no autenticado"
            ));
        }

        log.info("Updating contact for player {}: {}", player.getId(), contactData);

        try {
            String telefono = contactData.get("telefono");
            String telegramUsername = contactData.get("telegramUsername");

            // Обновляем поля
            if (telefono != null && !telefono.trim().isEmpty()) {
                player.setTelefono(telefono.trim());
            }
            if (telegramUsername != null && !telegramUsername.trim().isEmpty()) {
                player.setTelegramUsername(telegramUsername.trim());
            }

            // Сохраняем
            tournamentService.updatePlayerContacts(player);

            log.info("Contact updated for player {}: phone={}, telegram={}",
                    player.getId(), player.getTelefono(), player.getTelegramUsername());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Contacto actualizado correctamente"
            ));

        } catch (Exception e) {
            log.error("Error updating contact", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Error al actualizar contacto: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/api/players/me/contact-status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getContactStatus(
            @AuthenticationPrincipal Object principal) {

        PlayerPadel player = extractPlayerFromPrincipal(principal);

        if (player == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Usuario no autenticado"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "hasContact", player.hasValidContact(),
                "telefono", player.getTelefono(),
                "telegramUsername", player.getTelegramUsername()
        ));
    }
}