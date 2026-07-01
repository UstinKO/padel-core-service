package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.OwnerDto;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.OwnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owners")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;

    @GetMapping("/super-admin")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<OwnerDto> getSuperAdmin() {
        return ownerService.getSuperAdmin()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<OwnerDto> getOwnerById(@PathVariable Long id, @AuthenticationPrincipal Owner currentOwner) {
        return ResponseEntity.ok(ownerService.getOwnerById(id, currentOwner));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<OwnerDto> updateOwner(@PathVariable Long id, @Valid @RequestBody OwnerDto ownerDto, @AuthenticationPrincipal Owner currentOwner) {
            OwnerDto updated = ownerService.updateOwner(id, ownerDto, currentOwner);
            return ResponseEntity.ok(updated);
    }
}