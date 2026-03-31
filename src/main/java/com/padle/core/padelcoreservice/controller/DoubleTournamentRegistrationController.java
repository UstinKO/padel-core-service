package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.service.DoubleTournamentRegistrationService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/tournaments/double")
@RequiredArgsConstructor
public class DoubleTournamentRegistrationController {

    private final DoubleTournamentRegistrationService doubleRegistrationService;
    private final TournamentService tournamentService; // Добавляем для получения TournamentDto

    @PostMapping("/{tournamentId}/register")
    public ResponseEntity<?> registerForDoubleTournament(
            @PathVariable Long tournamentId,
            @Valid @RequestBody PartnerRegistrationDto partnerDto,
            @AuthenticationPrincipal Object principal) {

        Map<String, Object> response = new HashMap<>();

        Long currentUserId;
        try {
            currentUserId = extractCurrentUserId(principal);
        } catch (SecurityException e) {
            response.put("success", false);
            response.put("message", "Usuario no autenticado");
            return ResponseEntity.status(401).body(response);
        }

        try {
            TournamentDto tournamentDto = tournamentService.getTournamentDtoById(tournamentId)
                    .orElseThrow(() -> new RuntimeException("Torneo no encontrado con id: " + tournamentId));

            TournamentRegistrationDto registration = doubleRegistrationService
                    .registerForDoubleTournament(tournamentDto, currentUserId, partnerDto);

            response.put("success", true);
            response.put("message", "Registro completado");
            response.put("status", registration.getStatus());
            response.put("position", registration.getPosition());
            response.put("waitlistPosition", registration.getWaitlistPosition());

            return ResponseEntity.ok(response);

        } catch (TournamentRegistrationException e) {
            // Бизнес-ошибки (включая само-регистрацию, уже зарегистрирован и т.д.)
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Fallback на случай если constraint сработал раньше нашей проверки
            response.put("success", false);
            response.put("message", "Ya estás registrado en este torneo o los datos del compañero son inválidos.");
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error al procesar el registro: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<TournamentRegistrationDto> confirmPartnerRegistration(
            @RequestParam String token) {

        TournamentRegistrationDto registration = doubleRegistrationService
                .confirmPartnerRegistration(token);

        return ResponseEntity.ok(registration);
    }

    @PostMapping("/complete")
    public ResponseEntity<TournamentRegistrationDto> completePartnerRegistration(
            @AuthenticationPrincipal Object principal,
            @RequestParam(required = false) String email) {

        Long partnerId = extractCurrentUserId(principal);

        TournamentRegistrationDto registration = doubleRegistrationService
                .completePartnerRegistration(partnerId, email);

        return ResponseEntity.ok(registration);
    }

    private Long extractCurrentUserId(Object principal) {
        PlayerPadel player = SecurityUtils.extractPlayer(principal);
        if (player == null) {
            throw new SecurityException("Usuario no autenticado");
        }
        return player.getId();
    }
}