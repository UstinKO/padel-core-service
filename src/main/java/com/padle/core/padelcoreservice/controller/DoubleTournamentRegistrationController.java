package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.LookingForPartnerDto;
import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import com.padle.core.padelcoreservice.dto.SoloDoubleRegistrationDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.service.DoubleTournamentRegistrationService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tournaments/double")
@RequiredArgsConstructor
@Slf4j
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

    @PostMapping("/{tournamentId}/register-solo")
    public ResponseEntity<?> registerSoloForDoubleTournament(
            @PathVariable Long tournamentId,
            @Valid @RequestBody SoloDoubleRegistrationDto dto,
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
            TournamentRegistrationDto registration = doubleRegistrationService
                    .registerSoloForDoubleTournament(tournamentId, currentUserId, dto);

            response.put("success", true);
            response.put("message", "SEARCH".equals(dto.getSoloType())
                    ? "Te agregamos a la lista de jugadores que buscan compañero"
                    : "Inscripción registrada. Recordá agregar tu compañero antes del torneo");
            response.put("status", registration.getStatus());

            return ResponseEntity.ok(response);

        } catch (TournamentRegistrationException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error al procesar el registro: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/{tournamentId}/add-partner")
    public ResponseEntity<?> addPartnerToSoloRegistration(
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
            TournamentRegistrationDto registration = doubleRegistrationService
                    .addPartnerToSoloRegistration(tournamentId, currentUserId, partnerDto);

            response.put("success", true);
            response.put("message", "¡Compañero agregado exitosamente!");
            response.put("status", registration.getStatus());

            return ResponseEntity.ok(response);

        } catch (TournamentRegistrationException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error al agregar compañero: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/{tournamentId}/looking-for-partner")
    public ResponseEntity<List<LookingForPartnerDto>> getLookingForPartnerPlayers(
            @PathVariable Long tournamentId) {
        return ResponseEntity.ok(doubleRegistrationService.getLookingForPartnerPlayers(tournamentId));
    }

    @PostMapping("/{tournamentId}/propose-pair/{targetRegistrationId}")
    public ResponseEntity<?> proposePair(
            @PathVariable Long tournamentId,
            @PathVariable Long targetRegistrationId,
            @AuthenticationPrincipal Object principal) {

        Long currentUserId;
        try {
            currentUserId = extractCurrentUserId(principal);
        } catch (SecurityException e) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "No autenticado"));
        }
        try {
            doubleRegistrationService.proposePair(tournamentId, currentUserId, targetRegistrationId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Propuesta enviada. El jugador recibirá un email para aceptar."));
        } catch (TournamentRegistrationException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error proposing pair: tournamentId={}, proposer={}, target={}: {}",
                    tournamentId, currentUserId, targetRegistrationId, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/accept-pair")
    public ResponseEntity<?> acceptPairProposal(@RequestParam String token) {
        try {
            doubleRegistrationService.acceptPairProposal(token);
            return ResponseEntity.ok(Map.of("success", true, "message", "¡Pareja confirmada!"));
        } catch (TournamentRegistrationException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Error al confirmar"));
        }
    }

    private Long extractCurrentUserId(Object principal) {
        PlayerPadel player = SecurityUtils.extractPlayer(principal);
        if (player == null) {
            throw new SecurityException("Usuario no autenticado");
        }
        return player.getId();
    }
}