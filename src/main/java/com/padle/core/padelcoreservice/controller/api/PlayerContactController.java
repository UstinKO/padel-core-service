package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.security.oauth2.CustomOAuth2User;
import com.padle.core.padelcoreservice.service.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/players")
@RequiredArgsConstructor
@Slf4j
public class PlayerContactController {

    private final PlayerRepository playerRepository;
    private final PlayerService playerService;

    /**
     * Обновление контактных данных игрока (телефон и/или Telegram).
     *
     * Вызывается из модального окна на странице турнира / дашборде,
     * когда игрок пытается зарегистрироваться на турнир, но у него
     * нет ни телефона +54, ни Telegram ника.
     *
     * После сохранения фронт продолжает регистрацию на турнир.
     */
    @PatchMapping("/me/contact")
    public ResponseEntity<Map<String, Object>> updateContact(
            @RequestBody UpdateContactRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "No autenticado"));
        }

        PlayerPadel player = extractPlayer(authentication.getPrincipal());

        if (player == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "Usuario no encontrado"));
        }

        // Валидация: хотя бы одно поле должно быть заполнено
        boolean hasPhone    = request.getTelefono() != null
                && !request.getTelefono().isBlank();
        boolean hasTelegram = request.getTelegramUsername() != null
                && !request.getTelegramUsername().isBlank();

        if (!hasPhone && !hasTelegram) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false,
                            "message", "Debes ingresar al menos un dato de contacto"));
        }

        // Обновляем данные
        if (hasPhone) {
            String newPhone = request.getTelefono().trim();
            if (!newPhone.equals(player.getTelefono())
                    && playerService.existsByTelefono(newPhone)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false,
                                "message", "Este número de teléfono ya está en uso por otro jugador"));
            }
            player.setTelefono(newPhone);
        }
        if (hasTelegram) {
            String tg = request.getTelegramUsername().trim();
            // Добавляем @ если не указан
            if (!tg.startsWith("@")) {
                tg = "@" + tg;
            }
            player.setTelegramUsername(tg);
        }

        try {
            playerRepository.save(player);
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false,
                            "message", "Este número de teléfono ya está en uso por otro jugador"));
        }
        log.info("Player {} updated contact: phone={}, telegram={}",
                player.getId(),
                hasPhone ? "set" : "unchanged",
                hasTelegram ? "set" : "unchanged");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Datos de contacto guardados",
                "telefono", player.getTelefono() != null ? player.getTelefono() : "",
                "telegramUsername", player.getTelegramUsername() != null ? player.getTelegramUsername() : ""
        ));
    }

    /**
     * Проверяет, есть ли у текущего игрока контактные данные.
     * Фронт вызывает это перед показом кнопки регистрации на турнир.
     */
    @GetMapping("/me/contact-status")
    public ResponseEntity<Map<String, Object>> getContactStatus(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("hasContact", false));
        }

        PlayerPadel player = extractPlayer(authentication.getPrincipal());

        if (player == null) {
            return ResponseEntity.status(401).body(Map.of("hasContact", false));
        }

        // ДОБАВЬ ЛОГИРОВАНИЕ
        log.info("Contact status for player {}: phone={}, telegram={}, hasContact={}",
                player.getId(),
                player.getTelefono(),
                player.getTelegramUsername(),
                player.hasValidContact());

        return ResponseEntity.ok(Map.of(
                "hasContact", player.hasValidContact(),
                "hasPhone", player.getTelefono() != null && !player.getTelefono().isBlank(),
                "hasTelegram", player.getTelegramUsername() != null && !player.getTelegramUsername().isBlank(),
                "telefono", player.getTelefono() != null ? player.getTelefono() : "",
                "telegramUsername", player.getTelegramUsername() != null ? player.getTelegramUsername() : ""
        ));
    }

    /**
     * Универсальный метод для извлечения PlayerPadel из principal
     */
    private PlayerPadel extractPlayer(Object principal) {
        if (principal == null) {
            return null;
        }

        if (principal instanceof PlayerPadel) {
            return (PlayerPadel) principal;
        }

        if (principal instanceof CustomOAuth2User) {
            return ((CustomOAuth2User) principal).getPlayer();
        }

        if (principal instanceof UserDetails) {
            // Для обычной формы логина
            String email = ((UserDetails) principal).getUsername();
            return playerRepository.findByEmail(email).orElse(null);
        }

        log.warn("Unknown principal type: {}", principal.getClass().getName());
        return null;
    }

    @GetMapping("/search")
    public ResponseEntity<List<PlayerResponseDto>> searchPartners(
            @RequestParam String query,
            @RequestParam Long tournamentId,
            Authentication authentication) {

        if (query == null || query.trim().length() < 2) {
            return ResponseEntity.ok(List.of());
        }

        PlayerPadel currentPlayer = extractPlayer(authentication.getPrincipal());
        Long excludeId = currentPlayer != null ? currentPlayer.getId() : -1L;

        return ResponseEntity.ok(
                playerService.searchPartnerCandidates(query.trim(), tournamentId, excludeId)
        );
    }

    // ── DTO ──────────────────────────────────────────────────────────────────

    public static class UpdateContactRequest {
        private String telefono;
        private String telegramUsername;

        public String getTelefono() { return telefono; }
        public void setTelefono(String telefono) { this.telefono = telefono; }
        public String getTelegramUsername() { return telegramUsername; }
        public void setTelegramUsername(String telegramUsername) { this.telegramUsername = telegramUsername; }
    }
}