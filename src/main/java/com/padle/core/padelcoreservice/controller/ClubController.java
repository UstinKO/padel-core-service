package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.ClubDto;
import com.padle.core.padelcoreservice.service.ClubService;
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
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubController {

    private final ClubService clubService;

    @GetMapping
    public ResponseEntity<List<ClubDto>> getAllClubs() {
        return ResponseEntity.ok(clubService.getAllClubs());
    }

    @GetMapping("/active")
    public ResponseEntity<List<ClubDto>> getActiveClubs() {
        return ResponseEntity.ok(clubService.getActiveClubs());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClubDto> getClubById(@PathVariable Long id) {
        return clubService.getClubById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/nombre/{nombre}")
    public ResponseEntity<ClubDto> getClubByNombre(@PathVariable String nombre) {
        return clubService.getClubByNombre(nombre)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/zona/{zona}")
    public ResponseEntity<List<ClubDto>> getClubsByZona(@PathVariable String zona) {
        return ResponseEntity.ok(clubService.getClubsByZona(zona));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ClubDto>> searchClubs(@RequestParam String q) {
        return ResponseEntity.ok(clubService.searchClubs(q));
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ClubDto> createClub(@Valid @RequestBody ClubDto clubDto,
                                              Authentication authentication) {
        // Получаем ID создателя (нужно будет заменить на реальный из токена)
        Long createdBy = 1L; // Временно, потом заменим на реальный ID из токена
        try {
            ClubDto created = clubService.createClub(clubDto, createdBy);
            return ResponseEntity.ok(created);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ClubDto> updateClub(@PathVariable Long id,
                                              @Valid @RequestBody ClubDto clubDto) {
        return clubService.updateClub(id, clubDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> deleteClub(@PathVariable Long id) {
        if (clubService.deleteClub(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}/hard")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<Void> hardDeleteClub(@PathVariable Long id) {
        if (clubService.hardDeleteClub(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}