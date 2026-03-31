package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
        return tournamentService.getTournamentDtoById(id)
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<TournamentDto> createTournament(@Valid @RequestBody TournamentDto tournamentDto,
                                                          Authentication authentication) {
        Owner owner = extractOwner(authentication);
        return ResponseEntity.ok(tournamentService.createTournament(tournamentDto, owner.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<TournamentDto> updateTournament(@PathVariable Long id,
                                                          @Valid @RequestBody TournamentDto tournamentDto,
                                                          Authentication authentication) {
        Owner owner = extractOwner(authentication);
        return tournamentService.updateTournament(id, tournamentDto, owner.getId(), owner.isSuperAdmin())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<TournamentDto> updateTournamentStatus(@PathVariable Long id,
                                                                @RequestParam TournamentStatus status,
                                                                Authentication authentication) {
        Owner owner = extractOwner(authentication);
        return tournamentService.updateTournamentStatus(id, status, owner.getId(), owner.getId(), owner.isSuperAdmin())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<Void> deleteTournament(@PathVariable Long id,
                                                 Authentication authentication) {
        Owner owner = extractOwner(authentication);

        // Проверяем права на удаление
        if (!owner.isSuperAdmin()) {
            TournamentDto tournament = tournamentService.getTournamentDtoById(id)
                    .orElseThrow(() -> new RuntimeException("Tournament not found"));
            if (!tournament.getOwnerId().equals(owner.getId())) {
                throw new SecurityException("No tienes permiso para eliminar este torneo");
            }
        }

        if (tournamentService.deleteTournament(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<List<TournamentDto>> getMyTournaments(Authentication authentication) {
        Owner owner = extractOwner(authentication);
        return ResponseEntity.ok(tournamentService.getTournamentsForOwner(owner.getId(), owner.isSuperAdmin()));
    }

    private Owner extractOwner(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("Not authenticated");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Owner) {
            return (Owner) principal;
        }
        throw new SecurityException("Invalid principal type");
    }
}