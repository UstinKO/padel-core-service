package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.LookingForPartnerDto;
import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import com.padle.core.padelcoreservice.dto.SoloDoubleRegistrationDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.exception.PlayerLevelRequiredException;
import com.padle.core.padelcoreservice.exception.PairRegistrationException;
import com.padle.core.padelcoreservice.exception.PairRegistrationException.Reason;
import com.padle.core.padelcoreservice.security.RequestIdFilter;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.service.DoubleTournamentRegistrationService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

@RestController
@RequestMapping("/api/tournaments/double")
@RequiredArgsConstructor
@Slf4j
public class DoubleTournamentRegistrationController {

    private final DoubleTournamentRegistrationService doubleRegistrationService;
    private final TournamentService tournamentService;
    private final Validator validator;
    private final MessageSource messageSource;

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
            return pairError(401, Reason.SIGN_IN_REQUIRED);
        }

        try {
            if (partnerDto.getExistingUserId() == null && (Boolean.TRUE.equals(partnerDto.getIsExistingUser())
                    || !validator.validate(partnerDto, PartnerRegistrationDto.ManualPartner.class).isEmpty())) {
                return pairError(400, Reason.INVALID_PARTNER);
            }
            TournamentDto tournamentDto = tournamentService.getTournamentDtoById(tournamentId)
                    .orElseThrow(() -> new PairRegistrationException(Reason.TOURNAMENT_NOT_FOUND));

            TournamentRegistrationDto registration = doubleRegistrationService
                    .registerForDoubleTournament(tournamentDto, currentUserId, partnerDto);

            response.put("success", true);
            response.put("message", "Registro completado");
            response.put("status", registration.getStatus());
            response.put("position", registration.getPosition());
            response.put("waitlistPosition", registration.getWaitlistPosition());

            return ResponseEntity.ok(response);

        } catch (PlayerLevelRequiredException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "code", "PLAYER_LEVEL_REQUIRED", "message", e.getMessage(), "needLevel", true));
        } catch (PairRegistrationException e) {
            return pairError(e.getReason() == Reason.TOURNAMENT_NOT_FOUND ? 404 : 400, e.getReason());
        } catch (TournamentRegistrationException e) {
            return pairError(400, Reason.REGISTRATION_REJECTED);
        } catch (DataIntegrityViolationException e) {
            log.warn("Pair registration integrity conflict: tournamentId={}, requestId={}", tournamentId,
                    MDC.get(RequestIdFilter.MDC_KEY));
            return pairError(400, Reason.REGISTRATION_CONFLICT);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Pair registration failed: tournamentId={}, requestId={}, type={}", tournamentId,
                    MDC.get(RequestIdFilter.MDC_KEY), e.getClass().getSimpleName());
            return pairError(500, Reason.REGISTRATION_FAILED);
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmPartnerRegistration(@RequestParam String token) {
        try {
            TournamentRegistrationDto registration = doubleRegistrationService
                    .confirmPartnerRegistration(token);
            return ResponseEntity.ok(registration);

        } catch (TournamentRegistrationException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));

        } catch (DataIntegrityViolationException e) {
            // Запасной рубеж на случай гонки, которую не поймал advisory-lock
            // (confirmPartnerRegistration) — не падать 500 на уникальном индексе Telegram.
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "message", "Este número de teléfono o Telegram ya está registrado"));

        } catch (Exception e) {
            log.error("Error confirming partner registration: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Error al confirmar"));
        }
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

        } catch (PlayerLevelRequiredException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("needLevel", true);
            return ResponseEntity.badRequest().body(response);

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
            return pairError(401, Reason.SIGN_IN_REQUIRED);
        }

        try {
            if (partnerDto.getExistingUserId() == null && (Boolean.TRUE.equals(partnerDto.getIsExistingUser())
                    || !validator.validate(partnerDto, PartnerRegistrationDto.ManualPartner.class).isEmpty())) {
                return pairError(400, Reason.INVALID_PARTNER);
            }
            TournamentRegistrationDto registration = doubleRegistrationService
                    .addPartnerToSoloRegistration(tournamentId, currentUserId, partnerDto);

            response.put("success", true);
            response.put("message", "¡Compañero agregado exitosamente!");
            response.put("status", registration.getStatus());

            return ResponseEntity.ok(response);

        } catch (PlayerLevelRequiredException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "code", "PLAYER_LEVEL_REQUIRED", "message", e.getMessage(), "needLevel", true));
        } catch (PairRegistrationException e) {
            return pairError(e.getReason() == Reason.TOURNAMENT_NOT_FOUND ? 404 : 400, e.getReason());
        } catch (TournamentRegistrationException e) {
            return pairError(400, Reason.REGISTRATION_REJECTED);
        } catch (DataIntegrityViolationException e) {
            log.warn("Pair registration integrity conflict: tournamentId={}, requestId={}", tournamentId,
                    MDC.get(RequestIdFilter.MDC_KEY));
            return pairError(400, Reason.REGISTRATION_CONFLICT);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Pair registration failed: tournamentId={}, requestId={}, type={}", tournamentId,
                    MDC.get(RequestIdFilter.MDC_KEY), e.getClass().getSimpleName());
            return pairError(500, Reason.REGISTRATION_FAILED);
        }
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<?> invalidRegistrationRequest() {
        return pairError(400, Reason.INVALID_PARTNER);
    }

    private ResponseEntity<?> pairError(int status, Reason reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("code", reason.name());
        body.put("message", messageSource.getMessage("pair.registration." + reason.name().toLowerCase(Locale.ROOT),
                null, LocaleContextHolder.getLocale()));
        Optional.ofNullable(MDC.get(RequestIdFilter.MDC_KEY)).ifPresent(id -> body.put("requestId", id));
        return ResponseEntity.status(status).body(body);
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
            var registration = doubleRegistrationService.acceptPairProposal(token);
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(Map.of(
                    "success", true, "message", "¡Pareja confirmada!", "status", registration.getStatus()));
        } catch (TournamentRegistrationException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Error al confirmar"));
        }
    }

    @PostMapping("/decline-pair")
    public ResponseEntity<Map<String, Object>> declinePairProposal(@RequestParam String token) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Map.of("status", doubleRegistrationService.declinePairProposal(token)));
    }

    private Long extractCurrentUserId(Object principal) {
        PlayerPadel player = SecurityUtils.extractPlayer(principal);
        if (player == null) {
            throw new SecurityException("Usuario no autenticado");
        }
        return player.getId();
    }
}