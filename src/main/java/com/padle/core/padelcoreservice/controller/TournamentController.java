package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService tournamentService;

    @GetMapping
    public ResponseEntity<List<TournamentDto>> getAllTournaments() {
        return ResponseEntity.ok(tournamentService.getAllTournaments());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<TournamentDto>> getUpcomingTournaments() {
        return ResponseEntity.ok(tournamentService.getUpcomingTournaments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TournamentDto> getTournamentById(@PathVariable Long id) {
        return tournamentService.getTournamentById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/club/{clubId}")
    public ResponseEntity<List<TournamentDto>> getTournamentsByClub(@PathVariable Long clubId) {
        return ResponseEntity.ok(tournamentService.getTournamentsByClub(clubId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<TournamentDto>> getTournamentsByStatus(@PathVariable TournamentStatus status) {
        return ResponseEntity.ok(tournamentService.getTournamentsByStatus(status));
    }

    @GetMapping("/search")
    public ResponseEntity<List<TournamentDto>> searchTournaments(
            @RequestParam(required = false) Long clubId,
            @RequestParam(required = false) GenderFormat genero,
            @RequestParam(required = false) String nivel,
            @RequestParam(required = false) TournamentType tipo,
            @RequestParam(required = false) TournamentStatus estado) {
        return ResponseEntity.ok(tournamentService.searchTournaments(clubId, genero, nivel, tipo, estado));
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<TournamentDto> createTournament(@Valid @RequestBody TournamentDto tournamentDto,
                                                          Authentication authentication) {
        // Получаем ID создателя из Authentication
        Long createdBy = getCurrentUserId(authentication);
        return ResponseEntity.ok(tournamentService.createTournament(tournamentDto, createdBy));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<TournamentDto> updateTournament(@PathVariable Long id,
                                                          @Valid @RequestBody TournamentDto tournamentDto) {
        return tournamentService.updateTournament(id, tournamentDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<TournamentDto> updateTournamentStatus(@PathVariable Long id,
                                                                @RequestParam TournamentStatus status,
                                                                Authentication authentication) {
        Long updatedBy = getCurrentUserId(authentication);
        return tournamentService.updateTournamentStatus(id, status, updatedBy)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> deleteTournament(@PathVariable Long id) {
        if (tournamentService.deleteTournament(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // Вспомогательный метод для получения ID текущего пользователя
    private Long getCurrentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() != null) {
            // Здесь нужно реализовать логику получения ID пользователя из Principal
            // В зависимости от того, как у вас реализована аутентификация
            // Например, если в Principal хранится объект User с getId()
            // return ((User) authentication.getPrincipal()).getId();

            // Временное решение - возвращаем 1
            return 1L;
        }
        return null;
    }
}