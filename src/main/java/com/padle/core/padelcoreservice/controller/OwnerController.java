package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.OwnerDto;
import com.padle.core.padelcoreservice.service.OwnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/owners")
@RequiredArgsConstructor
public class OwnerController {

    private final OwnerService ownerService;

    @GetMapping("/super-admin")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<OwnerDto> getSuperAdmin() {
        return ownerService.getSuperAdmin()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<OwnerDto> getOwnerById(@PathVariable Long id) {
        return ownerService.getOwnerById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<OwnerDto> updateOwner(@PathVariable Long id, @RequestBody OwnerDto ownerDto) {
        try {
            OwnerDto updated = ownerService.updateOwner(id, ownerDto);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}