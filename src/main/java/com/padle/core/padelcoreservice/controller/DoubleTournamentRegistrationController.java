package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
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

        Long currentUserId = extractCurrentUserId(principal);

        TournamentDto tournamentDto = tournamentService.getTournamentDtoById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Torneo no encontrado con id: " + tournamentId));

        TournamentRegistrationDto registration = doubleRegistrationService
                .registerForDoubleTournament(tournamentDto, currentUserId, partnerDto);

        // Возвращаем такой же формат, как в обычной регистрации
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Registro completado");
        response.put("status", registration.getStatus());
        response.put("position", registration.getPosition());
        response.put("waitlistPosition", registration.getWaitlistPosition());

        return ResponseEntity.ok(response);
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