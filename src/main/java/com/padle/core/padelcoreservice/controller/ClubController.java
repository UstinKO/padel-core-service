package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.ClubDto;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.ClubService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<List<ClubDto>> getAllClubs() {
        return ResponseEntity.ok(clubService.getAllClubs());
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<List<ClubDto>> getActiveClubs() {
        return ResponseEntity.ok(clubService.getActiveClubs());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<ClubDto> getClubById(@PathVariable Long id) {
        return clubService.getClubById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/nombre/{nombre}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<ClubDto> getClubByNombre(@PathVariable String nombre) {
        return clubService.getClubByNombre(nombre)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/zona/{zona}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<List<ClubDto>> getClubsByZona(@PathVariable String zona) {
        return ResponseEntity.ok(clubService.getClubsByZona(zona));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<List<ClubDto>> searchClubs(@RequestParam String q) {
        return ResponseEntity.ok(clubService.searchClubs(q));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<ClubDto> createClub(@Valid @RequestBody ClubDto clubDto,
                                              @AuthenticationPrincipal Owner currentOwner) {
        try {
            ClubDto created = clubService.createClub(clubDto, currentOwner.getId());
            return ResponseEntity.ok(created);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ClubDto> updateClub(@PathVariable Long id,
                                              @Valid @RequestBody ClubDto clubDto, @AuthenticationPrincipal Owner currentOwner) {
        return ResponseEntity.ok().body(clubService.updateClub(id, clubDto, currentOwner));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<Void> deleteClub(@PathVariable Long id, @AuthenticationPrincipal Owner currentOwner) {
        clubService.deleteClub(id, currentOwner);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/hard")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<Void> hardDeleteClub(@PathVariable Long id, @AuthenticationPrincipal Owner currentOwner) {
        clubService.hardDeleteClub(id, currentOwner);
        return ResponseEntity.noContent().build();
    }
}