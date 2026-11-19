package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.exception.ClaimPhoneInUseException;
import com.padle.core.padelcoreservice.exception.ContactValidationException;
import com.padle.core.padelcoreservice.service.PlayerContactService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.security.oauth2.CustomOAuth2User;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.service.TournamentService;
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
    private final TournamentService tournamentService;
    private final PlayerContactService contacts;
    private final MessageSource messages;

    /** Сохраняет обязательный телефон и необязательный Telegram перед записью. */
    @PatchMapping("/me/contact")
    public ResponseEntity<ContactResponse> updateContact(
            @RequestBody UpdateContactRequest request, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return contactError(401, "SIGN_IN_REQUIRED", "");
        }
        PlayerPadel player = extractPlayer(authentication.getPrincipal());
        if (player == null) return contactError(401, "SIGN_IN_REQUIRED", "");
        try {
            var contact = contacts.update(player.getId(), request.telefono(), request.phoneCountry(), request.telegramUsername());
            return ResponseEntity.ok(new ContactResponse(true, "", "", message("SAVED"),
                    contact.telefono(), contact.telegramUsername()));
        } catch (ContactValidationException e) {
            return contactError(400, e.code(), e.field());
        } catch (ClaimPhoneInUseException e) {
            return contactError(409, "PHONE_IN_USE", "telefono");
        } catch (DataIntegrityViolationException e) {
            return contactError(409, "CONTACT_IN_USE", "");
        }
    }

    private ResponseEntity<ContactResponse> contactError(int status, String code, String field) {
        return ResponseEntity.status(status).body(new ContactResponse(false, code, field, message(code), "", ""));
    }

    private String message(String code) {
        return messages.getMessage("contact.validation." + code, null, LocaleContextHolder.getLocale());
    }

    public record UpdateContactRequest(String telefono, String phoneCountry, String telegramUsername) {}
    public record ContactResponse(boolean success, String code, String field, String message,
                                  String telefono, String telegramUsername) {}

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

        player = playerRepository.findById(player.getId()).orElse(player);

        return ResponseEntity.ok(Map.of(
                "hasContact", player.hasValidContact(),
                "hasPhone", player.getTelefono() != null && !player.getTelefono().isBlank(),
                "hasTelegram", player.getTelegramUsername() != null && !player.getTelegramUsername().isBlank(),
                "telefono", player.getTelefono() != null ? player.getTelefono() : "",
                "telegramUsername", player.getTelegramUsername() != null ? player.getTelegramUsername() : ""
        ));
    }

    /**
     * «Мои мероприятия» для клиента 1Padel.App — только чтение: предстоящие турниры
     * со статусом/позицией в листе ожидания собственной регистрации, и история.
     * Индивидуальная запись и отмена — внутри приложения через
     * {@code POST /players/tournaments/{id}/register|cancel}; парная регистрация/отмена
     * ({@code .../cancel-double}) — только через сайт.
     */
    @GetMapping("/me/tournaments")
    public ResponseEntity<?> getMyTournaments(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "No autenticado"));
        }

        PlayerPadel player = extractPlayer(authentication.getPrincipal());
        if (player == null) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", "Solo disponible para jugadores"));
        }

        TournamentService.PlayerTournamentsOverview overview = tournamentService.getMyTournamentsOverview(player.getId());
        return ResponseEntity.ok(Map.of(
                "upcoming", overview.upcoming(),
                "historical", overview.historical()
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

}
